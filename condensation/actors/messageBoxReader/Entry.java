package condensation.actors.messageBoxReader;

import condensation.serialization.Hash;
import condensation.stores.Store;

public class Entry {
	final Hash hash;

	// State
	boolean processed = false;
	Store waitingForStore = null;

	Entry(Hash hash) {
		this.hash = hash;
	}
}
