package condensation.messaging;

import java.util.Collection;

import condensation.serialization.Hash;
import condensation.stores.Store;

public class RequiredTransfer {
	public final Collection<Hash> hashes;
	public final Store sourceStore;
	public final String context;

	public RequiredTransfer(Collection<Hash> hashes, Store sourceStore, String context) {
		this.hashes = hashes;
		this.sourceStore = sourceStore;
		this.context = context;
	}
}
