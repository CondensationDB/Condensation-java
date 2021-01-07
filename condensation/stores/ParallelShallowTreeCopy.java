package condensation.stores;

import androidx.annotation.NonNull;

import java.util.HashSet;

import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.tasks.AwaitCounter;

// Copies objects from a source store to a destination store.
// The copy is shallow: if an object exists on the destination, it is supposed to be complete.
// Objects are scheduled in parallel, which causes a high load and requires a lot of memory.
public class ParallelShallowTreeCopy {
	public final Store source;
	public final Store destination;
	public final long graceTime;

	HashSet<Hash> copied = new HashSet<>();
	HashSet<Hash> previouslyCopied = new HashSet<>();
	long lastSwap = 0L;

	public ParallelShallowTreeCopy(Store source, Store destination, long graceTime) {
		this.source = source;
		this.destination = destination;
		this.graceTime = graceTime;
	}

	public void copy(Hash hash, KeyPair signer, Done done) {
		// Swap the copied hashes if necessary
		long now = System.currentTimeMillis();
		if (lastSwap + graceTime < now) {
			previouslyCopied = copied;
			copied = new HashSet<>();
			lastSwap = now;
		}

		// Copy
		new CopyGroup(hash, signer, done);
	}

	class CopyGroup implements AwaitCounter.Done {
		final KeyPair keyPair;
		final Done done;

		boolean hasError = false;

		CopyGroup(Hash hash, KeyPair keyPair, Done done) {
			this.keyPair = keyPair;
			this.done = done;
			AwaitCounter awaitCounter = new AwaitCounter();
			new Copy(hash, awaitCounter);
			awaitCounter.then(this);
		}

		@Override
		public void onAwaitCounterDone() {
			if (hasError) done.onTreeCopyFailed();
			else done.onTreeCopyDone();
		}

		class Copy implements Store.BookDone, Store.GetDone, Store.PutDone, AwaitCounter.Done {
			final Hash hash;
			final AwaitCounter awaitCounter;
			CondensationObject object = null;

			Copy(Hash hash, AwaitCounter awaitCounter) {
				this.hash = hash;
				this.awaitCounter = awaitCounter;

				if (hasError) return;
				if (copied.contains(hash)) return;
				if (previouslyCopied.contains(hash)) return;

				awaitCounter.await();
				destination.book(hash, keyPair, this);
			}

			@Override
			public void onBookDone() {
				success();
			}

			@Override
			public void onBookNotFound() {
				// Get the object from the local store
				source.get(hash, keyPair, this);
			}

			@Override
			public void onBookStoreError(@NonNull String error) {
				error();
			}

			@Override
			public void onGetDone(@NonNull CondensationObject object) {
				this.object = object;

				// Check all children
				AwaitCounter awaitCounter = new AwaitCounter();
				for (Hash child : object.hashes())
					new Copy(child, awaitCounter);
				awaitCounter.then(this);
			}

			@Override
			public void onGetNotFound() {
				success();
			}

			@Override
			public void onGetStoreError(@NonNull String error) {
				error();
			}

			@Override
			public void onAwaitCounterDone() {
				// Upload this object
				destination.put(hash, object, keyPair, this);
			}

			@Override
			public void onPutDone() {
				success();
			}

			@Override
			public void onPutStoreError(@NonNull String error) {
				error();
			}

			void success() {
				copied.add(hash);
				awaitCounter.done();
			}

			void error() {
				hasError = true;
				awaitCounter.done();
			}
		}
	}

	/*
	// Pulls from the source (slow) to the destination (fast).
	public boolean fullPull(Hash hash) {
		if (copied.contains(hash)) return true;

		Bytes destinationBytes = destination.get(hash);
		if (destinationBytes != null) {
			// This object is OK, but check all children
			CondensationObject object = CondensationObject.from(destinationBytes);
			if (object != null)
				for (Hash child : object.hashes())
					if (!fullPull(child)) return false;
		} else {
			// Get the object
			Bytes bytes = source.get(hash);
			if (bytes == null) return false;

			// Process children first ...
			CondensationObject object = CondensationObject.from(bytes);
			if (object != null)
				for (Hash child : object.hashes())
					if (!fullPull(child)) return false;

			// ... and only then store this object
			if (!destination.put(hash, bytes, keyPair)) return false;
		}

		return success(hash);
	}

	// Pushes from the source (fast) to the destination (slow).
	public boolean fullPush(Hash hash) {
		if (copied.contains(hash)) return true;

		if (destination.has(hash)) {
			// Try to get the object from the fast source
			Bytes bytes = source.get(hash);
			if (bytes == null) bytes = destination.get(hash);
			if (bytes == null) return false;

			// Check the children
			CondensationObject object = CondensationObject.from(bytes);
			if (object != null)
				for (Hash child : object.hashes())
					if (!fullPush(child)) return false;
		} else {
			// Get the object from the local store
			Bytes bytes = source.get(hash);
			if (bytes == null) return false;

			// Check the children
			CondensationObject object = CondensationObject.from(bytes);
			if (object != null)
				for (Hash child : object.hashes())
					if (!fullPush(child)) return false;

			// Upload this object
			if (!destination.put(hash, bytes, keyPair)) return false;
		}

		return success(hash);
	}
	*/

	public interface Done {
		void onTreeCopyDone();

		void onTreeCopyFailed();
	}
}
