package condensation.actors;

import androidx.annotation.NonNull;

import condensation.crypto.RSAPublicKey;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

public class PublicKey implements Comparable<PublicKey> {
	// Static

	public static PublicKey from(CondensationObject object) {
		RSAPublicKey rsaPublicKey = RSAPublicKey.from(object);
		if (rsaPublicKey == null) return null;
		return new PublicKey(object.calculateHash(), object, rsaPublicKey);
	}

	// Object

	public final Hash hash;
	public final CondensationObject object;
	public final RSAPublicKey rsaPublicKey;

	public PublicKey(Hash hash, CondensationObject object, RSAPublicKey rsaPublicKey) {
		this.hash = hash;
		this.object = object;
		this.rsaPublicKey = rsaPublicKey;
	}

	public Bytes encrypt(Bytes bytes) {
		return rsaPublicKey.encrypt(bytes);
	}

	public boolean verify(Bytes digest, byte[] signature) {
		return rsaPublicKey.verify(digest, signature);
	}

	public boolean verify(Bytes digest, Bytes signature) {
		return rsaPublicKey.verify(digest, signature.toByteArray());
	}

	public boolean verify(Hash hash, byte[] signature) {
		return rsaPublicKey.verify(hash.bytes, signature);
	}

	public boolean verify(Hash hash, Bytes signature) {
		return rsaPublicKey.verify(hash.bytes, signature.toByteArray());
	}

	public boolean equals(Object that) {
		return that instanceof PublicKey && (this == that || equals((PublicKey) that));
	}

	public boolean equals(PublicKey that) {
		return that != null && hash.equals(that.hash);
	}

	@Override
	public int hashCode() {
		return hash.hashCode();
	}

	@Override
	public int compareTo(@NonNull PublicKey publicKey) {
		return hash.compareTo(publicKey.hash);
	}
}
