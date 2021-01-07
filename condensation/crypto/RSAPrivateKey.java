package condensation.crypto;

import java.math.BigInteger;

import condensation.serialization.Bytes;

public class RSAPrivateKey {
	// Public exponent
	public final BigInteger e;
	// Prime 1
	public final BigInteger p;
	// Prime 2
	public final BigInteger q;
	// Modulus n = p * q
	public final BigInteger n;
	// Private exponent d = modInverse(e, (p - 1) * (q - 1))
	public final BigInteger d;
	// dpInt = d mod (p - 1)
	public final BigInteger dP;
	// dq = d mod (q - 1)
	public final BigInteger dQ;
	// modInverse(q, p)
	public final BigInteger qInv;

	public RSAPrivateKey(BigInteger e, BigInteger p, BigInteger q) {
		this.e = e;
		this.p = p;
		this.q = q;

		// Calculate the rest
		n = p.multiply(q);
		BigInteger p1 = p.subtract(BigInteger.ONE);
		BigInteger q1 = q.subtract(BigInteger.ONE);
		BigInteger phi = p1.multiply(q1);
		d = e.modInverse(phi);
		dP = d.remainder(p1);
		dQ = d.remainder(q1);
		qInv = q.modInverse(p);
	}

	public RSAPublicKey publicKey() {
		return new RSAPublicKey(e, n);
	}

	public BigInteger privateCrypt(BigInteger input) {
		// Simple, but slow method
		//return input.modPow(d, n);

		// More complicated, but faster method based on the Chinese Remainder Theorem

		// mP = ((input mod p) ^ dP)) mod p
		BigInteger mP = input.remainder(p).modPow(dP, p);

		// mQ = ((input mod q) ^ dQ)) mod q
		BigInteger mQ = input.remainder(q).modPow(dQ, q);

		// h = qInv * (mP - mQ) mod p
		BigInteger h = mP.subtract(mQ).multiply(qInv).mod(p);    // mod (in Java) returns the positive residual

		// m = h * q + mQ
		return h.multiply(q).add(mQ);
	}

	public BigInteger publicCrypt(BigInteger input) {
		return input.modPow(e, n);
	}

	public Bytes sign(byte[] buffer, int byteOffset, int byteLength) {
		BigInteger pss = RSAEncoding.generatePSS(buffer, byteOffset, byteLength);
		BigInteger signature = privateCrypt(pss);
		return RSAEncoding.lowermost256Bytes(signature);
	}

	public boolean verify(Bytes digest, byte[] signatureBytes) {
		BigInteger signature = new BigInteger(1, signatureBytes);
		BigInteger pss = publicCrypt(signature);
		return RSAEncoding.verifyPSS(digest.buffer, digest.byteOffset, digest.byteLength, pss);
	}

	public Bytes encrypt(Bytes message) {
		BigInteger oaep = RSAEncoding.encodeOAEP(message.buffer, message.byteOffset, message.byteLength);
		BigInteger encrypted = publicCrypt(oaep);
		return RSAEncoding.lowermost256Bytes(encrypted);
	}

	public byte[] decrypt(byte[] encryptedBytes) {
		BigInteger encrypted = new BigInteger(1, encryptedBytes);
		BigInteger oaep = privateCrypt(encrypted);
		return RSAEncoding.decodeOAEP(oaep);
	}
}
