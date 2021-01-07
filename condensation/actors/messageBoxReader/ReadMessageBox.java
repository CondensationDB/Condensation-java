package condensation.actors.messageBoxReader;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;

import condensation.Condensation;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.stores.BoxLabel;
import condensation.stores.Store;
import condensation.tasks.AwaitCounter;
import condensation.tasks.RateLimitedTaskQueue;

class ReadMessageBox implements Runnable, Store.ListDone, AwaitCounter.Done {
	public final MessageBoxReader messageBoxReader;
	final MessageBoxReader.Delegate delegate;
	final RateLimitedTaskQueue taskQueue;
	final AwaitCounter awaitCounter = new AwaitCounter();
	boolean hasError = false;

	ReadMessageBox(MessageBoxReader messageBoxReader, MessageBoxReader.Delegate delegate) {
		this.messageBoxReader = messageBoxReader;
		this.delegate = delegate;
		this.taskQueue = messageBoxReader.pool.taskQueue(messageBoxReader.actorOnStore.store);
		taskQueue.enqueue(this);
	}

	@Override
	public void run() {
		awaitCounter.await();
		messageBoxReader.actorOnStore.store.list(messageBoxReader.actorOnStore.publicKey.hash, BoxLabel.MESSAGES, 0L, messageBoxReader.pool.keyPair, this);
		awaitCounter.then(this);
	}

	@Override
	public void onListDone(ArrayList<Hash> hashes) {
		// Process new entries
		HashMap<Hash, Entry> newEntries = new HashMap<>();
		for (Hash hash : hashes) {
			Entry existing = messageBoxReader.entries.get(hash);
			Entry entry = existing == null ? new Entry(hash) : existing;
			newEntries.put(hash, entry);
			if (entry.processed) continue;
			entry.processed = true;
			if (entry.waitingForStore == null) {
				new ReadMessage(this, entry);
			} else {
				new CheckStoreAvailability(this, entry);
			}
		}

		// Set the new entries
		messageBoxReader.entries = newEntries;
		awaitCounter.done();
		taskQueue.done();
	}

	@Override
	public void onListStoreError(@NonNull String error) {
		failWithStoreError(error);
	}

	void failWithStoreError(@NonNull String error) {
		hasError = true;
		awaitCounter.done();
		taskQueue.done();
	}

	@Override
	public void onAwaitCounterDone() {
		if (!hasError) delegate.onMessageBoxReadingDone();
		else delegate.onMessageBoxReadingFailed();
	}

	static class CheckStoreAvailability implements Runnable, Store.GetDone {
		final ReadMessageBox readMessageBox;
		final Entry entry;

		CheckStoreAvailability(ReadMessageBox readMessageBox, Entry entry) {
			this.readMessageBox = readMessageBox;
			this.entry = entry;
			readMessageBox.messageBoxReader.pool.taskQueue(entry.waitingForStore).enqueue(this);
		}

		@Override
		public void run() {
			// Check if the source store is available
			entry.waitingForStore.get(Condensation.emptyBytesHash, readMessageBox.messageBoxReader.pool.keyPair, this);
		}

		@Override
		public void onGetDone(@NonNull CondensationObject object) {
			// This should not happen, since the emptyBytesHash points to an empty byte sequence, which is not a valid object
			new ReadMessage(readMessageBox, entry);
		}

		@Override
		public void onGetNotFound() {
			// The store is available
			new ReadMessage(readMessageBox, entry);
		}

		@Override
		public void onGetStoreError(@NonNull String error) {
			// The store is still not available
			entry.processed = false;
		}
	}
}