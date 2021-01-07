package condensation.stores;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import condensation.serialization.Hash;

public final class BoxRemoval implements Comparable<BoxRemoval> {
	public static ArrayList<BoxRemoval> none = new ArrayList<>();

	public final Hash accountHash;
	public final BoxLabel boxLabel;
	public final Hash hash;

	public BoxRemoval(Hash accountHash, BoxLabel boxLabel, Hash hash) {
		this.accountHash = accountHash;
		this.boxLabel = boxLabel;
		this.hash = hash;
	}

	@Override
	public int compareTo(@NonNull BoxRemoval that) {
		int cmp0 = accountHash.compareTo(that.accountHash);
		if (cmp0 != 0) return cmp0;
		int cmp1 = boxLabel.compareTo(that.boxLabel);
		if (cmp1 != 0) return cmp1;
		return hash.compareTo(that.hash);
	}
}
