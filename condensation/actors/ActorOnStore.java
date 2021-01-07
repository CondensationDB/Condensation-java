package condensation.actors;

import condensation.stores.Store;

public class ActorOnStore {
	public final PublicKey publicKey;
	public final Store store;

	public ActorOnStore(PublicKey publicKey, Store store) {
		this.publicKey = publicKey;
		this.store = store;
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof ActorOnStore && (this == that || equals((ActorOnStore) that));
	}

	public boolean equals(ActorOnStore that) {
		if (that == null) return false;
		if (that == this) return true;
		return store.id.equals(that.store.id) && publicKey.hash.equals(that.publicKey.hash);
	}

	@Override
	public int hashCode() {
		return store.id.hashCode() ^ publicKey.hash.hashCode();
	}

	public static boolean equals(ActorOnStore a, ActorOnStore b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		return a.equals(b);
	}
}
