package condensation.actors;

import androidx.annotation.NonNull;
import condensation.serialization.Hash;
import condensation.stores.Store;

public class Actor {
	@NonNull
	public final KeyPair keyPair;
	@NonNull
	public final Store store;

	// Private data
	public final PrivateRoot privateRoot;

	public Actor(@NonNull KeyPair keyPair, @NonNull Store store) {
		this.keyPair = keyPair;
		this.store = store;

		// Private data on the storage store
		this.privateRoot = new PrivateRoot(keyPair, store);
	}

	// *** Our own actor

	public boolean isMe(Hash actorHash) {
		return keyPair.publicKey.hash.equals(actorHash);
	}
}
