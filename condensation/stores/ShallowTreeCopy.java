package condensation.stores;

import androidx.annotation.NonNull;

import java.util.HashSet;

import condensation.ImmutableStack;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

// Copies objects from a source store to a destination store.
// The copy is shallow: if an object exists on the destination, it is supposed to be complete.
public class ShallowTreeCopy {
	public final Store source;
	public final Store destination;
	public final long graceTime;

	HashSet<Hash> copied = new HashSet<>();
	HashSet<Hash> previouslyCopied = new HashSet<>();
	long lastSwap = 0L;

	public ShallowTreeCopy(Store source, Store destination, long graceTime) {
		this.source = source;
		this.destination = destination;
		this.graceTime = graceTime;
	}

	public void copy(Hash hash, KeyPair keyPair, Done done) {
		// Swap the copied hashes if necessary
		long now = System.currentTimeMillis();
		if (lastSwap + graceTime < now) {
			previouslyCopied = copied;
			copied = new HashSet<>();
			lastSwap = now;
		}

		// Copy
		new Copy(hash, keyPair, done);
	}

	class Copy implements Store.GetDone, Store.PutDone, Store.BookDone {
		final KeyPair keyPair;
		final Done done;

		ImmutableStack<ObjectToCopy> stack;

		Copy(Hash hash, KeyPair keyPair, Done done) {
			this.keyPair = keyPair;
			this.done = done;
			stack = stack.with(new ObjectToCopy(hash));
			processNext();
		}

		void processNext() {
			while (stack.length > 0) {
				if (!processHead()) return;
				stack = stack.tail;
			}

			done.onTreeCopyDone();
		}

		boolean processHead() {
			// Check if we have copied this hash before
			if (copied.contains(stack.head.hash)) return true;
			if (previouslyCopied.contains(stack.head.hash)) return true;

			if (stack.head.object == null) {
				// Book the object
				destination.book(stack.head.hash, keyPair, this);
			} else {
				// Upload it
				destination.put(stack.head.hash, stack.head.object, keyPair, this);
			}

			return false;
		}

		@Override
		public void onBookDone() {
			headDone();
		}

		@Override
		public void onBookNotFound() {
			// Get the object from the local store
			source.get(stack.head.hash, keyPair, this);
		}

		@Override
		public void onBookStoreError(@NonNull String error) {
			done.onTreeCopyFailed(stack.head.hash, error);
		}

		@Override
		public void onGetDone(@NonNull CondensationObject object) {
			stack.head.object = object;

			// Check all children
			for (Hash child : stack.head.object.hashes())
				stack = stack.with(new ObjectToCopy(child));

			processNext();
		}

		@Override
		public void onGetNotFound() {
			copied.add(stack.head.hash);
			headDone();
		}

		@Override
		public void onGetStoreError(@NonNull String error) {
			done.onTreeCopyFailed(stack.head.hash, error);
		}

		@Override
		public void onPutDone() {
			copied.add(stack.head.hash);
			headDone();
		}

		@Override
		public void onPutStoreError(@NonNull String error) {
			done.onTreeCopyFailed(stack.head.hash, error);
		}

		void headDone() {
			stack = stack.tail;
			processNext();
		}
	}

	static class ObjectToCopy {
		final Hash hash;
		CondensationObject object = null;

		ObjectToCopy(Hash hash) {
			this.hash = hash;
		}
	}

	public interface Done {
		void onTreeCopyDone();

		void onTreeCopyFailed(Hash hash, String error);
	}
}
