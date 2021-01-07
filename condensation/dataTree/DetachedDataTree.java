package condensation.dataTree;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import condensation.actors.KeyPair;
import condensation.stores.InMemoryStore;

// A data tree not attached to the private box
public class DetachedDataTree extends DataTree {

	public DetachedDataTree(KeyPair keyPair) {
		super(keyPair, InMemoryStore.create());
	}

	@NonNull
	public String toString() {
		return "DetachedDataTree";
	}

	@Override
	public void savingDone(long revision, Part newPart, ArrayList<Part> obsoleteParts) {
		// We don't do anything
		unsaved.savingDone();
	}
}
