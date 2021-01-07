package condensation.stores;

import androidx.annotation.NonNull;

import java.util.Collection;

import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

public class ObjectCache extends Store {
	public final Store backend;
	public final Store cache;

	public ObjectCache(Store backend, Store cache) {
		super("Object Cache\n" + backend.id + "\n" + cache.id);
		this.backend = backend;
		this.cache = cache;
	}

	@Override
	public void get(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull GetDone done) {
		new Get(hash, keyPair, done);
	}

	class Get {
		final Hash hash;
		final KeyPair keyPair;
		final GetDone done;

		Get(Hash hash, KeyPair keyPair, GetDone done) {
			this.hash = hash;
			this.keyPair = keyPair;
			this.done = done;
			cache.get(hash, keyPair, new GetLocal());
		}

		class GetLocal implements GetDone {
			@Override
			public void onGetDone(@NonNull CondensationObject object) {
				//CN.log("ObjectCache hit " + hash.shortHex());
				done.onGetDone(object);
			}

			@Override
			public void onGetNotFound() {
				//CN.log("ObjectCache miss " + hash.shortHex());
				backend.get(hash, keyPair, new GetRemote());
			}

			@Override
			public void onGetStoreError(@NonNull String error) {
				backend.get(hash, keyPair, new GetRemote());
			}
		}

		class GetRemote implements GetDone {
			@Override
			public void onGetDone(@NonNull CondensationObject object) {
				// Store locally
				cache.put(hash, object, keyPair, Store.ignore);

				// Return
				done.onGetDone(object);
			}

			@Override
			public void onGetNotFound() {
				done.onGetNotFound();
			}

			@Override
			public void onGetStoreError(@NonNull String error) {
				done.onGetStoreError(error);
			}
		}
	}

	@Override
	public void put(@NonNull Hash hash, @NonNull CondensationObject object, @NonNull KeyPair keyPair, @NonNull PutDone done) {
		cache.put(hash, object, keyPair, Store.ignore);
		backend.put(hash, object, keyPair, done);
	}

	@Override
	public void book(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull BookDone done) {
		cache.book(hash, keyPair, Store.ignore);
		backend.book(hash, keyPair, done);
	}

	@Override
	public void list(@NonNull Hash accountHash, @NonNull BoxLabel boxLabel, long timeout, @NonNull KeyPair keyPair, @NonNull ListDone done) {
		backend.list(accountHash, boxLabel, timeout, keyPair, done);
	}

	public void modify(@NonNull Collection<BoxAddition> additions, @NonNull Collection<BoxRemoval> removals, @NonNull KeyPair keyPair, @NonNull final ModifyDone done) {
		backend.modify(additions, removals, keyPair, done);
	}
}
