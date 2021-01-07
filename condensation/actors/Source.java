package condensation.actors;

import java.util.Collections;

import condensation.serialization.Hash;
import condensation.stores.BoxAddition;
import condensation.stores.BoxLabel;
import condensation.stores.BoxRemoval;
import condensation.stores.Store;

public class Source {
	public final KeyPair keyPair;
	public final ActorOnStore actorOnStore;
	public final BoxLabel boxLabel;
	public final Hash hash;

	// State
	int referenceCount = 1;

	public Source(KeyPair keyPair, ActorOnStore actorOnStore, BoxLabel boxLabel, Hash hash) {
		this.keyPair = keyPair;
		this.actorOnStore = actorOnStore;
		this.boxLabel = boxLabel;
		this.hash = hash;
	}

	public String toString() {
		return actorOnStore.publicKey.hash.hex() + "/" + boxLabel.asText + "/" + hash.hex();
	}

	public void keep() {
		if (referenceCount < 1)
			throw new Error("This source has already been discarded, and cannot be kept any more.");

		referenceCount += 1;
	}

	public void discard() {
		if (referenceCount < 1)
			throw new Error("This source has already been discarded.");

		referenceCount -= 1;
		if (referenceCount > 0) return;

		BoxRemoval removal = new BoxRemoval(actorOnStore.publicKey.hash, boxLabel, hash);
		actorOnStore.store.modify(BoxAddition.none, Collections.singleton(removal), keyPair, Store.ignore);
	}

	public int getReferenceCount() {
		return referenceCount;
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof Source && (this == that || equals((Source) that));
	}

	public boolean equals(Source that) {
		if (that == null) return false;
		if (that == this) return true;
		return keyPair.equals(that.keyPair) && actorOnStore.equals(that.actorOnStore) && boxLabel == that.boxLabel && hash.equals(that.hash);
	}

	@Override
	public int hashCode() {
		return keyPair.hashCode() ^ boxLabel.hashCode() ^ actorOnStore.hashCode() ^ hash.hashCode();
	}
}
