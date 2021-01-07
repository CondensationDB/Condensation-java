package condensation.stores;

import androidx.annotation.NonNull;

import java.util.Collection;
import java.util.HashSet;

import condensation.Condensation;
import condensation.ImmutableList;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

public class GetFromAnyStore extends Store {
	public static GetFromAnyStore create(Store... stores) {
		return new GetFromAnyStore(ImmutableList.create(stores));
	}

	private static String createId(ImmutableList<Store> stores) {
		StringBuilder text = new StringBuilder("Any Store");
		for (Store store : stores)
			text.append("\n").append(store.id);
		return text.toString();
	}

	final ImmutableList<Store> stores;

	public GetFromAnyStore(ImmutableList<Store> stores) {
		super(createId(stores));
		this.stores = stores;
	}

	@Override
	public void get(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull GetDone done) {
		new Get(hash, keyPair, done);
	}

	class Get implements GetDone {
		final Hash hash;
		final KeyPair keyPair;
		final GetDone done;

		// State
		int storesTried = 0;
		String errors = "";
		HashSet<String> triedIds;

		Get(Hash hash, KeyPair keyPair, GetDone done) {
			this.hash = hash;
			this.keyPair = keyPair;
			this.done = done;
		}

		void tryNextSource() {
			while (storesTried < stores.size()) {
				Store store = stores.get(storesTried);
				storesTried += 1;
				if (triedIds.contains(store.id)) continue;
				triedIds.add(store.id);
				store.get(hash, keyPair, this);
				return;
			}

			allFailed();
		}

		@Override
		public void onGetDone(@NonNull CondensationObject object) {
			done.onGetDone(object);
		}

		@Override
		public void onGetNotFound() {
			tryNextSource();
		}

		@Override
		public void onGetStoreError(@NonNull String error) {
			if (!errors.isEmpty()) errors += "\n";
			errors += error;
			tryNextSource();
		}

		void allFailed() {
			done.onGetStoreError(errors);
		}
	}

	@Override
	public void book(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull final BookDone done) {
		Condensation.mainThread.post(new Runnable() {
			@Override
			public void run() {
				done.onBookStoreError("This is a read-only object store.");
			}
		});
	}

	@Override
	public void put(@NonNull Hash hash, @NonNull CondensationObject object, @NonNull KeyPair keyPair, @NonNull final PutDone done) {
		Condensation.mainThread.post(new Runnable() {
			@Override
			public void run() {
				done.onPutStoreError("This is a read-only object store.");
			}
		});
	}

	@Override
	public void list(@NonNull Hash accountHash, @NonNull BoxLabel boxLabel, long timeout, @NonNull KeyPair keyPair, @NonNull final ListDone done) {
		Condensation.mainThread.post(new Runnable() {
			@Override
			public void run() {
				done.onListStoreError("This is a read-only object store.");
			}
		});
	}

	@Override
	public void modify(@NonNull Collection<BoxAddition> additions, @NonNull Collection<BoxRemoval> removals, @NonNull KeyPair keyPair, @NonNull final ModifyDone done) {
		Condensation.mainThread.post(new Runnable() {
			@Override
			public void run() {
				done.onModifyStoreError("This is a read-only object store.");
			}
		});
	}
}
