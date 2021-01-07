package condensation.serialization;

import androidx.annotation.NonNull;

public class HashAndKey {
	// *** Static ***

	public static boolean equals(HashAndKey a, HashAndKey b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		return a.equals(b);
	}

	// *** Object ***
	public final Hash hash;
	public final Bytes key;

	public HashAndKey(@NonNull Hash hash, @NonNull Bytes key) {
		this.hash = hash;
		this.key = key;
	}

	public String toString() {
		return "HashAndKey(" + hash.shortHex() + ", " + key.slice(0, 3).asHex() + "...)";
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof HashAndKey && equals((HashAndKey) that);
	}

	public boolean equals(HashAndKey that) {
		return that != null && hash.equals(that.hash);
	}
}
