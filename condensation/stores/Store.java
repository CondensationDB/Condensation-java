package condensation.stores;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;

import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

// Methods are called from the main thread, and must not block, but call the handler (on the main thread) when done.
// More than one method may be called at the same time.
public abstract class Store {
	// The store identifier. Two stores with the same id are supposed to be the same. The id is often derived from the URL.
	public final String id;

	public Store(String id) {
		this.id = id;
	}

	public String toString() {
		return id;
	}

	// Asynchronous object store interface

	public abstract void get(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull GetDone done);

	public interface GetDone {
		void onGetDone(@NonNull CondensationObject object);

		void onGetNotFound();

		void onGetStoreError(@NonNull String error);
	}

	public abstract void book(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull BookDone done);

	public interface BookDone {
		void onBookDone();

		void onBookNotFound();

		void onBookStoreError(@NonNull String error);
	}

	public abstract void put(@NonNull Hash hash, @NonNull CondensationObject object, @NonNull KeyPair keyPair, @NonNull PutDone done);

	public interface PutDone {
		void onPutDone();

		void onPutStoreError(@NonNull String error);
	}

	// Asynchronous account store interface

	public abstract void list(@NonNull Hash accountHash, @NonNull BoxLabel boxLabel, long timeout, @NonNull KeyPair keyPair, @NonNull ListDone done);

	public interface ListDone {
		void onListDone(ArrayList<Hash> hashes);

		void onListStoreError(@NonNull String error);
	}

	public abstract void modify(@NonNull Collection<BoxAddition> additions, @NonNull Collection<BoxRemoval> removals, @NonNull KeyPair keyPair, @NonNull ModifyDone done);

	public interface ModifyDone {
		void onModifyDone();

		void onModifyStoreError(@NonNull String error);
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof Store && equals((Store) that);
	}

	public boolean equals(Store that) {
		return id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	public static Ignore ignore = new Ignore();

	static class Ignore implements GetDone, PutDone, BookDone, ListDone, ModifyDone {
		@Override
		public void onGetDone(@NonNull CondensationObject object) {
		}

		@Override
		public void onGetNotFound() {
		}

		@Override
		public void onGetStoreError(@NonNull String error) {
		}

		@Override
		public void onPutDone() {
		}

		@Override
		public void onPutStoreError(@NonNull String error) {
		}

		@Override
		public void onBookDone() {
		}

		@Override
		public void onBookNotFound() {
		}

		@Override
		public void onBookStoreError(@NonNull String error) {
		}

		@Override
		public void onListDone(@NonNull ArrayList<Hash> hashes) {
		}

		@Override
		public void onListStoreError(@NonNull String error) {
		}

		@Override
		public void onModifyDone() {
		}

		@Override
		public void onModifyStoreError(@NonNull String error) {
		}
	}
}
