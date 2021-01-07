package condensation.stores;

import androidx.annotation.NonNull;

import java.util.Collection;

import condensation.Condensation;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.tasks.BackgroundTask;

public class HashVerificationStore extends Store {
	public final Store store;

	public HashVerificationStore(Store store) {
		super("Hash Verification Store\n  " + store.id);
		this.store = store;
	}

	@Override
	public void get(@NonNull final Hash hash, @NonNull KeyPair keyPair, @NonNull final GetDone done) {
		new Get(hash, keyPair, done);
	}

	class Get implements BackgroundTask, Store.GetDone {
		final Hash hash;
		final GetDone done;
		CondensationObject object;
		Hash calculatedHash;

		Get(Hash hash, KeyPair keyPair, GetDone done) {
			this.hash = hash;
			this.done = done;
			store.get(hash, keyPair, this);
		}

		@Override
		public void onGetDone(@NonNull CondensationObject object) {
			this.object = object;
			Condensation.computationExecutor.run(this);
		}

		@Override
		public void onGetNotFound() {
			done.onGetNotFound();
		}

		@Override
		public void onGetStoreError(@NonNull String error) {
			done.onGetStoreError(error);
		}

		@Override
		public void background() {
			calculatedHash = object.calculateHash();
		}

		@Override
		public void after() {
			// We treat wrong hashes as transmission errors, and therefore return "failed". The application may try again, and perhaps show the error.
			// If it were malicious attacks, we could treat them as "not found" (invalid), so that the application stops trying.
			if (Hash.equals(calculatedHash, hash)) done.onGetDone(object);
			else done.onGetStoreError("Hash mismatch.");
		}
	}

	@Override
	public void book(@NonNull final Hash hash, @NonNull KeyPair keyPair, @NonNull final BookDone done) {
		store.book(hash, keyPair, done);
	}

	@Override
	public void put(@NonNull final Hash hash, @NonNull final CondensationObject object, @NonNull KeyPair keyPair, @NonNull final PutDone done) {
		store.put(hash, object, keyPair, done);
	}

	@Override
	public void list(@NonNull final Hash accountHash, @NonNull final BoxLabel boxLabel, long timeout, @NonNull KeyPair keyPair, @NonNull final ListDone done) {
		store.list(accountHash, boxLabel, timeout, keyPair, done);
	}

	@Override
	public void modify(@NonNull final Collection<BoxAddition> additions, @NonNull final Collection<BoxRemoval> removals, @NonNull KeyPair keyPair, @NonNull final ModifyDone done) {
		store.modify(additions, removals, keyPair, done);
	}
}
