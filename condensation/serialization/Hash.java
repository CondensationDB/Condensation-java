package condensation.serialization;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import androidx.annotation.NonNull;
import condensation.crypto.SHA256;

public final class Hash implements Comparable<Hash> {
	// *** Object ***

	public final Bytes bytes;

	private Hash(@NonNull Bytes bytes) {
		this.bytes = bytes;
	}

	public String hex() {
		return bytes.asHex();
	}

	public String shortHex() {
		return hex().substring(0, 8) + "...";
	}

	public String toString() {
		return hex();
	}

	// *** Equality

	@Override
	public boolean equals(Object that) {
		return that instanceof Hash && (this == that || equals((Hash) that));
	}

	public boolean equals(Hash that) {
		if (that == null) return false;
		if (that == this) return true;
		return bytes.equals(that.bytes);
	}

	@Override
	public int hashCode() {
		return bytes.hashCode();
	}

	public static boolean equals(Hash a, Hash b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		return a.equals(b);
	}

	// *** Comparable

	@Override
	public int compareTo(@NonNull Hash that) {
		return bytes.compareTo(that.bytes);
	}

	// *** Static ***

	public static Hash from(String hashHex) {
		return from(Bytes.fromHex(hashHex));
	}

	public static Hash from(Bytes bytes) {
		if (bytes == null || bytes.byteLength != 32) return null;
		return new Hash(bytes);
	}

	public static Hash from(byte[] bytes) {
		if (bytes == null || bytes.length != 32) return null;
		return new Hash(new Bytes(bytes, 0, 32));
	}

	public static Hash calculateFor(@NonNull byte[] bytes) {
		MessageDigest sha256 = SHA256.createInstance();
		sha256.update(bytes);
		return from(sha256.digest());
	}

	public static Hash calculateFor(@NonNull byte[] bytes, int offset, int count) {
		MessageDigest sha256 = SHA256.createInstance();
		sha256.update(bytes, offset, count);
		return from(sha256.digest());
	}

	public static Hash calculateFor(@NonNull Bytes bytes) {
		MessageDigest sha256 = SHA256.createInstance();
		sha256.update(bytes.buffer, bytes.byteOffset, bytes.byteLength);
		return from(sha256.digest());
	}

	public static Hash calculateFor(@NonNull ByteBuffer bytes) {
		MessageDigest sha256 = SHA256.createInstance();
		sha256.update(bytes);
		return from(sha256.digest());
	}
}
