package condensation.crypto;

import java.math.BigInteger;

import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Record;

public class RSAPublicKey {
	static final Bytes bc_e = Bytes.fromText("e");
	static final Bytes bc_n = Bytes.fromText("n");

	public static RSAPublicKey from(CondensationObject condensationObject) {
		Record record = Record.from(condensationObject);
		if (record == null) return null;
		BigInteger e = new BigInteger(1, record.child(bc_e).bytesValue().toByteArray());
		BigInteger n = new BigInteger(1, record.child(bc_n).bytesValue().toByteArray());
		if (e.signum() <= 0 || n.signum() <= 0) return null;
		return new RSAPublicKey(e, n);
	}

	// Public exponent
	public final BigInteger e;
	// Modulus
	public final BigInteger n;

	public RSAPublicKey(BigInteger e, BigInteger n) {
		this.e = e;
		this.n = n;
	}

	public BigInteger publicCrypt(BigInteger input) {
		return input.modPow(e, n);
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
}
