package condensation.stores;

import androidx.annotation.NonNull;

import java.util.Collection;

import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

public class StoreWithUrl extends Store {
	final Store store;
	final String url;

	public StoreWithUrl(Store store, String url) {
		super("Store with URL\n" + store.id);
		this.store = store;
		this.url = url;
	}

	@Override
	public void get(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull GetDone done) {
		store.get(hash, keyPair, done);
	}

	@Override
	public void book(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull BookDone done) {
		store.book(hash, keyPair, done);
	}

	@Override
	public void put(@NonNull Hash hash, @NonNull CondensationObject object, @NonNull KeyPair keyPair, @NonNull PutDone done) {
		store.put(hash, object, keyPair, done);
	}

	@Override
	public void list(@NonNull Hash accountHash, @NonNull BoxLabel boxLabel, long timeout, @NonNull KeyPair keyPair, @NonNull ListDone done) {
		store.list(accountHash, boxLabel, timeout, keyPair, done);
	}

	@Override
	public void modify(@NonNull Collection<BoxAddition> additions, @NonNull Collection<BoxRemoval> removals, @NonNull KeyPair keyPair, @NonNull ModifyDone done) {
		store.modify(additions, removals, keyPair, done);
	}
}
