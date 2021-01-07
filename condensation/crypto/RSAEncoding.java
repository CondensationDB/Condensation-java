package condensation.crypto;

import java.math.BigInteger;
import java.security.DigestException;
import java.security.MessageDigest;

import condensation.Condensation;
import condensation.serialization.Bytes;

// Inspired by the code of BouncyCastle.org, OpenSSL, and RFC 3447
public class RSAEncoding {
	private static final int emLength = 256;    // = 2048 / 8
	private static final int hashLength = 32;
	private static final byte[] PSSPadding1 = new byte[8];  // 8 zeroes
	private static final byte[] OAEPZeroLabelHash = new byte[]{(byte) 0xe3, (byte) 0xb0, (byte) 0xc4, (byte) 0x42, (byte) 0x98, (byte) 0xfc, (byte) 0x1c, (byte) 0x14, (byte) 0x9a, (byte) 0xfb, (byte) 0xf4, (byte) 0xc8, (byte) 0x99, (byte) 0x6f, (byte) 0xb9, (byte) 0x24, (byte) 0x27, (byte) 0xae, (byte) 0x41, (byte) 0xe4, (byte) 0x64, (byte) 0x9b, (byte) 0x93, (byte) 0x4c, (byte) 0xa4, (byte) 0x95, (byte) 0x99, (byte) 0x1b, (byte) 0x78, (byte) 0x52, (byte) 0xb8, (byte) 0x55};

	public static boolean verifyPSS(byte[] digest, int digestOffset, int digestLength, BigInteger emInteger) {
		// Make it 256 bytes
		byte[] emBytes = emInteger.toByteArray();
		byte[] em;
		if (emBytes.length > emLength) {
			em = new byte[emLength];
			System.arraycopy(emBytes, emBytes.length - emLength, em, 0, emLength);
		} else if (emBytes.length < emLength) {
			em = new byte[emLength];
			System.arraycopy(emBytes, 0, em, emLength - emBytes.length, emBytes.length);
		} else {
			em = emBytes;
		}

		// Check the last byte
		if (em[emLength - 1] != (byte) 0xbc) return false;

		// Unmask the salt: zeros | 0x01 | salt = maskedDB ^ mask
		int dbLength = emLength - hashLength - 1;   // max. 223
		byte[] mask = maskGenerationFunction1(em, emLength - hashLength - 1, hashLength, dbLength);
		for (int i = 0; i < dbLength; i++) em[i] ^= mask[i];

		// The first byte may be incomplete
		em[0] &= 0x7f;

		// Remove leading zeros
		int n = 0;
		while (em[n] == 0 && n < dbLength) n++;

		// The first unmasked byte must be 0x01
		if (em[n] != 0x01) return false;
		n++;

		// The rest is salt (max. 222 bytes)
		int saltStart = n;
		int saltLength = dbLength - n;

		// Calculate H = SHA256(8 zeros | digest | salt)
		MessageDigest sha256 = SHA256.createInstance();
		sha256.update(PSSPadding1);
		sha256.update(digest, digestOffset, digestLength);
		sha256.update(em, saltStart, saltLength);
		byte[] h = sha256.digest();

		// Verify h
		for (int i = 0; i < 32; i++)
			if (h[i] != em[dbLength + i]) return false;

		return true;
	}

	public static BigInteger generatePSS(byte[] digest, int digestOffset, int digestLength) {
		// Prepare the salt
		int saltLength = 32;
		byte[] salt = new byte[saltLength];
		Condensation.secureRandom.nextBytes(salt);

		// Calculate H = SHA256(8 zeros | digest | salt)
		MessageDigest sha256 = SHA256.createInstance();
		sha256.update(PSSPadding1);
		sha256.update(digest, digestOffset, digestLength);
		sha256.update(salt);

		// Prepare the message = maskedDB | H | 0xbc
		byte[] em = new byte[emLength];
		em[emLength - 1] = (byte) 0xbc;
		try {
			sha256.digest(em, emLength - hashLength - 1, hashLength);
		} catch (DigestException e) {
			return null;
		}

		// Write maskedDB = (zeros | 0x01 | salt) ^ mask
		int dbLen = emLength - hashLength - 1;
		byte[] mask = maskGenerationFunction1(em, emLength - hashLength - 1, hashLength, dbLen);

		// Zeros
		int n = 0;
		for (; n < dbLen - saltLength - 1; n++)
			em[n] = mask[n];

		// 0x01
		em[n] = (byte) (0x01 ^ mask[n]);
		n++;

		// Salt
		for (int i = 0; i < saltLength; i++, n++)
			em[n] = (byte) (salt[i] ^ mask[n]);

		// Set the first bit to 0, because the signature can only be 2048 - 1 bit long
		em[0] &= 0x7f;

		return new BigInteger(1, em);
	}

	public static BigInteger encodeOAEP(byte[] message, int messageOffset, int messageLength) {
		// Create DB = labelHash | zeros | 0x01 | message
		int dbLength = emLength - hashLength - 1;
		byte[] db = new byte[dbLength];
		System.arraycopy(OAEPZeroLabelHash, 0, db, 0, 32);
		db[dbLength - messageLength - 1] = (byte) 0x01;
		System.arraycopy(message, messageOffset, db, dbLength - messageLength, messageLength);

		// Create seed
		byte[] seed = new byte[hashLength];
		Condensation.secureRandom.nextBytes(seed);

		// Prepare the encoded message
		byte[] em = new byte[emLength];

		// Write maskedDB = DB ^ MGF1(seed)
		byte[] dbMask = maskGenerationFunction1(seed, 0, hashLength, dbLength);
		int n = emLength - dbLength;
		for (int i = 0; i < dbLength; i++, n++)
			em[n] = (byte) (db[i] ^ dbMask[i]);

		// Write maskedSeed = seed ^ MGF1(maskedDB)
		byte[] seedMask = maskGenerationFunction1(em, emLength - dbLength, dbLength, hashLength);
		n = 1;
		for (int i = 0; i < hashLength; i++, n++)
			em[n] = (byte) (seed[i] ^ seedMask[i]);

		return new BigInteger(1, em);
	}

	public static byte[] decodeOAEP(BigInteger emInteger) {
		// Add zeros so that we have 256 bytes
		byte[] emBytes = emInteger.toByteArray();
		if (emBytes.length > emLength) return null;
		byte[] em = new byte[emLength];
		System.arraycopy(emBytes, 0, em, emLength - emBytes.length, emBytes.length);

		// Extract the seed
		int dbLength = emLength - hashLength - 1;
		byte[] seedMask = maskGenerationFunction1(em, emLength - dbLength, dbLength, hashLength);
		byte[] seed = new byte[hashLength];
		int n = 1;
		for (int i = 0; i < hashLength; i++, n++)
			seed[i] = (byte) (em[n] ^ seedMask[i]);

		// Prepare the DB mask
		byte[] dbMask = maskGenerationFunction1(seed, 0, hashLength, dbLength);

		// To guard against timing attacks, we just keep a correct flag, and continue processing
		// even if the sequence is clearly wrong. (Note that on some systems, the compiler might
		// optimize this and return directly whenever we set correct = false.)
		boolean correct = true;

		// Verify the label hash
		int i = 0;
		for (; i < OAEPZeroLabelHash.length; n++, i++)
			if (OAEPZeroLabelHash[i] != (byte) (em[n] ^ dbMask[i])) correct = false;

		// Consume the PS (zeros)
		for (; em[n] == dbMask[i] && n < emLength; n++) i++;

		// Consume the 0x01 byte
		if (n >= emLength || (byte) (em[n] ^ dbMask[i]) != (byte) 0x01) correct = false;
		n++;
		i++;

		// Unmask the message
		byte[] message = new byte[emLength - n];
		for (int k = 0; n < emLength; n++, i++, k++)
			message[k] = (byte) (em[n] ^ dbMask[i]);

		return correct ? message : null;
	}

	private static byte[] maskGenerationFunction1(byte[] seed, int seedOffset, int seedLength, int maskLength) {
		// Allocate memory
		int blocks = (maskLength - 1) / 32 + 1;
		byte[] mask = new byte[blocks * 32];

		// Write blocks
		byte[] counter = new byte[4];
		MessageDigest sha256 = SHA256.createInstance();
		for (int i = 0; i < blocks; i++) {
			sha256.update(seed, seedOffset, seedLength);
			counter[3] = (byte) i;
			sha256.update(counter, 0, counter.length);
			try {
				sha256.digest(mask, i * 32, 32);
			} catch (DigestException e) {
				// This is fatal
				System.exit(1);
				return mask;
			}
		}

		return mask;
	}

	static Bytes lowermost256Bytes(BigInteger bigInteger) {
		byte[] byteArray = bigInteger.toByteArray();
		if (byteArray.length < 256) {
			Bytes bytes = new Bytes(256);
			bytes.set(256 - byteArray.length, byteArray);
			return bytes;
		} else {
			return new Bytes(byteArray, byteArray.length - 256, 256);
		}
	}
}
