package condensation.stores;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

public final class BoxAddition implements Comparable<BoxAddition> {
	public static final ArrayList<BoxAddition> none = new ArrayList<>();

	public final Hash accountHash;
	public final BoxLabel boxLabel;
	public final Hash hash;
	public final CondensationObject object;

	public BoxAddition(Hash accountHash, BoxLabel boxLabel, Hash hash, CondensationObject object) {
		this.accountHash = accountHash;
		this.boxLabel = boxLabel;
		this.hash = hash;
		this.object = object;
	}

	@Override
	public int compareTo(@NonNull BoxAddition that) {
		int cmp0 = accountHash.compareTo(that.accountHash);
		if (cmp0 != 0) return cmp0;
		int cmp1 = boxLabel.compareTo(that.boxLabel);
		if (cmp1 != 0) return cmp1;
		return hash.compareTo(that.hash);
	}
}
