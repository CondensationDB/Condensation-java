package condensation.stores;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import condensation.Condensation;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

public class Transfer {
	// Copy operations are "slow", i.e. each transfer only schedules one store operation at a time.
	// This in turn reduces the memory footprint (cached objects) and the queue length, which in turn reduces potential stalling of concurrent operations.
	// If an already running operation is scheduled, the operations are merged/joined.

	public final KeyPair keyPair;
	public final Store source;
	public final Store destination;
	public final Done done;
	public final long started = getOrder();

	// State
	private final Iterator<Hash> hashes;
	private final HashSet<Hash> copiedObjects = new HashSet<>();

	public Transfer(KeyPair keyPair, final Collection<Hash> hashes, Store source, Store destination, final Done done) {
		this.keyPair = keyPair;
		this.hashes = hashes.iterator();
		this.source = source;
		this.destination = destination;
		this.done = done;

		processNextHash();
	}

	private void processNextHash() {
		if (!hashes.hasNext()) {
			done.onTransferDone();
			return;
		}

		new TransferObject(hashes.next(), hashDone);
	}

	private final Done hashDone = new Done() {
		@Override
		public void onTransferDone() {
			processNextHash();
		}

		@Override
		public void onTransferMissingObject(@NonNull MissingObject missingObject) {
			done.onTransferMissingObject(missingObject);
		}

		@Override
		public void onTransferStoreError(@NonNull Store store, @NonNull String error) {
			done.onTransferStoreError(store, error);
		}
	};

	class TransferObject implements Store.BookDone, Store.GetDone, Done, Store.PutDone {
		private final Hash hash;
		private final Done done;

		private HeldObject heldObject;
		private int copiedChildren = 0;

		TransferObject(final Hash hash, final Done done) {
			this.hash = hash;
			this.done = done;

			// Check if we have copied this object before
			if (copiedObjects.contains(hash)) {
				Condensation.mainThread.post(new Runnable() {
					@Override
					public void run() {
						done.onTransferDone();
					}
				});
				return;
			}

			copiedObjects.add(hash);

			// Book the object on the destination store
			Transfer.book(destination, hash, keyPair, started, this);
		}

		@Override
		public void onBookDone() {
			done.onTransferDone();
		}

		@Override
		public void onBookNotFound() {
			// Check if this object is currently cached as part of another operation
			heldObject = heldObjects.get(hash);
			if (heldObject != null) {
				heldObject.acquire();
				processNextChild();
				return;
			}

			// Retrieve the object
			Transfer.get(source, hash, keyPair, started, this);
		}

		@Override
		public void onBookStoreError(@NonNull String error) {
			allFailed(destination, error);
		}

		@Override
		public void onGetDone(@NonNull CondensationObject object) {
			heldObject = heldObjects.get(hash);
			if (heldObject != null) {
				heldObject.acquire();
			} else {
				heldObject = new HeldObject(hash, object);
				heldObjects.put(hash, heldObject);
			}

			processNextChild();
		}

		@Override
		public void onGetNotFound() {
			done.onTransferMissingObject(new MissingObject(hash, source));
		}

		@Override
		public void onGetStoreError(@NonNull String error) {
			allFailed(source, error);
		}

		void processNextChild() {
			if (copiedChildren >= heldObject.object.hashesCount) {
				Transfer.put(destination, hash, heldObject.object, keyPair, this);
				return;
			}

			new TransferObject(heldObject.object.hashAtIndex(copiedChildren), this);
			copiedChildren += 1;
		}

		@Override
		public void onTransferDone() {
			processNextChild();
		}

		@Override
		public void onTransferMissingObject(@NonNull MissingObject missingObject) {
			missingObject.path.add(hash);
			done.onTransferMissingObject(missingObject);
		}

		@Override
		public void onTransferStoreError(@NonNull Store store, @NonNull String error) {
			allFailed(store, error);
		}

		@Override
		public void onPutDone() {
			heldObject.release();
			done.onTransferDone();
		}

		@Override
		public void onPutStoreError(@NonNull String error) {
			allFailed(destination, error);
		}

		void allFailed(Store store, String error) {
			Condensation.log("Transfer error with store " + store.id + " -- " + error + " -- " + hash.shortHex());
			if (heldObject != null) heldObject.release();
			done.onTransferStoreError(store, error);
		}
	}

	// *** Objects currently held in memory

	private static final HashMap<Hash, HeldObject> heldObjects = new HashMap<>();

	private static class HeldObject {
		final Hash hash;
		final CondensationObject object;
		long counter = 1;

		HeldObject(Hash hash, CondensationObject object) {
			this.hash = hash;
			this.object = object;
		}

		void acquire() {
			counter += 1;
		}

		void release() {
			counter -= 1;
			if (counter > 0) return;
			heldObjects.remove(hash);
		}
	}

	// *** Object requests

	private static long orderCounter = 0L;

	static long getOrder() {
		orderCounter++;
		return orderCounter;
	}

	static class ObjectRequest {
		final Store store;
		final Hash hash;
		final KeyPair keyPair;

		ObjectRequest(Store store, Hash hash, KeyPair keyPair) {
			this.store = store;
			this.hash = hash;
			this.keyPair = keyPair;
		}

		// *** Equality

		@Override
		public boolean equals(Object that) {
			return that instanceof ObjectRequest && (this == that || equals((ObjectRequest) that));
		}

		public boolean equals(ObjectRequest that) {
			if (that == null) return false;
			if (that == this) return true;
			return keyPair.publicKey.hash.equals(that.keyPair.publicKey.hash) && hash.equals(that.hash) && store.equals(that.store);
		}

		@Override
		public int hashCode() {
			return (keyPair.publicKey.hash.hashCode() << 1) ^ hash.hashCode() ^ store.hashCode();
		}
	}

	// *** Running book operations

	private static final HashMap<ObjectRequest, BookOperation> bookOperations = new HashMap<>();

	public static void book(Store destination, Hash hash, KeyPair keyPair, long availableSince, Store.BookDone done) {
		ObjectRequest request = new ObjectRequest(destination, hash, keyPair);
		BookOperation operation = bookOperations.get(request);
		if (operation == null) operation = new BookOperation(request);
		operation.listeners.add(new BookDone(done, availableSince));
	}

	private static class BookDone {
		final Store.BookDone done;
		final long ifStartedAfter;

		private BookDone(Store.BookDone done, long ifStartedAfter) {
			this.done = done;
			this.ifStartedAfter = ifStartedAfter;
		}
	}

	private static class BookOperation implements Store.BookDone {
		final ObjectRequest request;
		final ArrayList<BookDone> listeners = new ArrayList<>();
		final long operationStart = getOrder();

		BookOperation(ObjectRequest request) {
			this.request = request;

			bookOperations.put(request, this);
			request.store.book(request.hash, request.keyPair, this);
		}

		@Override
		public void onBookDone() {
			bookOperations.remove(request);
			for (BookDone entry : listeners)
				entry.done.onBookDone();
		}

		@Override
		public void onBookNotFound() {
			bookOperations.remove(request);
			for (BookDone entry : listeners)
				if (operationStart > entry.ifStartedAfter)
					entry.done.onBookNotFound();
				else
					book(request.store, request.hash, request.keyPair, entry.ifStartedAfter, entry.done);
		}

		@Override
		public void onBookStoreError(@NonNull String error) {
			bookOperations.remove(request);
			for (BookDone entry : listeners) {
				if (operationStart > entry.ifStartedAfter)
					entry.done.onBookStoreError(error);
				else
					book(request.store, request.hash, request.keyPair, entry.ifStartedAfter, entry.done);
			}
		}
	}

	// *** Running get operations

	private static final HashMap<ObjectRequest, GetOperation> getOperations = new HashMap<>();

	public static void get(Store store, Hash hash, KeyPair keyPair, long availableSince, Store.GetDone done) {
		ObjectRequest request = new ObjectRequest(store, hash, keyPair);
		GetOperation operation = getOperations.get(request);
		if (operation == null) operation = new GetOperation(request);
		operation.listeners.add(new GetDone(done, availableSince));
	}

	private static class GetDone {
		final Store.GetDone done;
		final long ifStartedAfter;

		private GetDone(Store.GetDone done, long ifStartedAfter) {
			this.done = done;
			this.ifStartedAfter = ifStartedAfter;
		}
	}

	private static class GetOperation implements Store.GetDone {
		final ObjectRequest request;
		final ArrayList<GetDone> listeners = new ArrayList<>();
		final long operationStart = getOrder();

		GetOperation(ObjectRequest request) {
			this.request = request;

			getOperations.put(request, this);
			request.store.get(request.hash, request.keyPair, this);
		}

		@Override
		public void onGetDone(@NonNull CondensationObject object) {
			getOperations.remove(request);
			for (GetDone entry : listeners)
				entry.done.onGetDone(object);
		}

		@Override
		public void onGetNotFound() {
			// The object may have been added while this get operation was running, but we obviously didn't see it yet.
			// Get requests that joined this operation later therefore have to be given a second chance, because they may
			// have been started after the object was added. This should be rare, however.
			getOperations.remove(request);
			for (GetDone entry : listeners)
				if (operationStart > entry.ifStartedAfter)
					entry.done.onGetNotFound();
				else
					get(request.store, request.hash, request.keyPair, entry.ifStartedAfter, entry.done);
		}

		@Override
		public void onGetStoreError(@NonNull String error) {
			getOperations.remove(request);
			for (GetDone entry : listeners)
				if (operationStart > entry.ifStartedAfter)
					entry.done.onGetStoreError(error);
				else
					get(request.store, request.hash, request.keyPair, entry.ifStartedAfter, entry.done);
		}
	}

	// *** Running put operations

	private static final HashMap<ObjectRequest, PutOperation> putOperations = new HashMap<>();

	public static void put(Store store, Hash hash, CondensationObject object, KeyPair keyPair, Store.PutDone done) {
		ObjectRequest request = new ObjectRequest(store, hash, keyPair);
		PutOperation operation = putOperations.get(request);
		if (operation == null) operation = new PutOperation(request, object);
		operation.listeners.add(done);
	}

	private static class PutOperation implements Store.PutDone {
		final ObjectRequest request;
		final CondensationObject object;
		final ArrayList<Store.PutDone> listeners = new ArrayList<>();

		PutOperation(ObjectRequest request, CondensationObject object) {
			this.request = request;
			this.object = object;

			putOperations.put(request, this);
			request.store.put(request.hash, object, request.keyPair, this);
		}

		@Override
		public void onPutDone() {
			putOperations.remove(request);
			for (Store.PutDone listener : listeners)
				listener.onPutDone();
		}

		@Override
		public void onPutStoreError(@NonNull String error) {
			putOperations.remove(request);
			for (Store.PutDone listener : listeners)
				listener.onPutStoreError(error);
		}
	}

	public interface Done {
		void onTransferDone();

		void onTransferMissingObject(@NonNull MissingObject missingObject);

		void onTransferStoreError(@NonNull Store store, @NonNull String error);
	}

	public static void log() {
		Condensation.log("Current get operations: " + getOperations.size() + ", put " + putOperations.size() + ", book " + bookOperations.size() + ", held " + heldObjects.size());
	}
}
