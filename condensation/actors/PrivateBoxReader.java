package condensation.actors;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.stores.BoxLabel;
import condensation.stores.GetAndDecryptRecord;
import condensation.stores.Store;
import condensation.tasks.AwaitCounter;
import condensation.tasks.RateLimitedTaskQueue;

public class PrivateBoxReader {
	public final KeyPair keyPair;
	public final ActorOnStore actorOnStore;

	// State
	private boolean isReading = false;
	private HashMap<Hash, Entry> entries = new HashMap<>();

	// We limit this to 4 tasks to (1) avoid flooding the download queue and (2) avoid piling up memory with half-processed entries
	RateLimitedTaskQueue taskQueue = new RateLimitedTaskQueue(4);

	public PrivateBoxReader(KeyPair keyPair, Store store) {
		this.keyPair = keyPair;
		this.actorOnStore = new ActorOnStore(keyPair.publicKey, store);
	}

	public boolean isReading() {
		return isReading;
	}

	public boolean read(ReadingDone done) {
		if (isReading) return false;
		taskQueue.enqueue(new ReadAccount(done));
		return true;
	}

	class ReadAccount implements Runnable, Store.ListDone, AwaitCounter.Done {
		final ReadingDone done;
		final AwaitCounter awaitCounter = new AwaitCounter();
		boolean hasStoreError = false;

		ReadAccount(ReadingDone done) {
			this.done = done;
			isReading = true;
		}

		@Override
		public void run() {
			awaitCounter.await();
			actorOnStore.store.list(actorOnStore.publicKey.hash, BoxLabel.PRIVATE, 0L, keyPair, this);
			awaitCounter.then(this);
		}

		@Override
		public void onListDone(ArrayList<Hash> hashes) {
			// Process new entries
			HashMap<Hash, Entry> newEntries = new HashMap<>();
			for (Hash hash : hashes) {
				Entry existing = entries.get(hash);
				Entry entry = existing == null ? new Entry(hash) : existing;
				newEntries.put(hash, entry);
				if (entry.processed) continue;
				entry.processed = true;
				new ReadEntry(entry);
			}

			// Set the new processed set
			entries = newEntries;
			awaitCounter.done();
			taskQueue.done();
		}

		@Override
		public void onListStoreError(@NonNull String error) {
			hasStoreError = true;
			awaitCounter.done();
			taskQueue.done();
		}

		class ReadEntry implements Runnable, Store.GetDone, GetAndDecryptRecord.Done {
			final Source source;
			final Entry entry;

			Record envelope;
			HashAndKey contentHashAndKey;

			ReadEntry(Entry entry) {
				this.entry = entry;
				this.source = new Source(keyPair, actorOnStore, BoxLabel.PRIVATE, entry.hash);
				awaitCounter.await();
				taskQueue.enqueue(this);
			}

			@Override
			public void run() {
				actorOnStore.store.get(entry.hash, keyPair, this);
			}

			@Override
			public void onGetDone(@NonNull CondensationObject object) {
				envelope = Record.from(object);
				if (envelope == null) {
					invalid("Envelope is not a record.");
					return;
				}

				// Read the content hash
				Hash contentHash = envelope.child(BC.content).hashValue();
				if (contentHash == null) {
					invalid("Missing content hash.");
					return;
				}

				// Verify the signature
				if (!Condensation.verifyEnvelopeSignature(envelope, keyPair.publicKey, contentHash)) {
					invalid("Invalid signature.");
					return;
				}

				// Decrypt the key
				Bytes aesKey = keyPair.decryptKeyOnEnvelope(envelope);
				if (aesKey == null) {
					Condensation.log("Envelope:" + envelope.toString());
					invalid("Not encrypted for us.");
					return;
				}

				contentHashAndKey = new HashAndKey(contentHash, aesKey);
				new GetAndDecryptRecord(contentHashAndKey, actorOnStore.store, keyPair, this);
			}

			@Override
			public void onGetNotFound() {
				invalid("Envelope object not found.");
			}

			@Override
			public void onGetStoreError(@NonNull String error) {
				hasStoreError = true;
				entry.processed = false;
				awaitCounter.done();
				taskQueue.done();
			}

			@Override
			public void onGetAndDecryptRecordDone(@NonNull Record record, @NonNull CondensationObject object) {
				done.onPrivateBoxEntry(source, envelope, contentHashAndKey, record);
				awaitCounter.done();
				taskQueue.done();
			}

			@Override
			public void onGetAndDecryptRecordInvalid(@NonNull String reason) {
				invalid(reason);
			}

			@Override
			public void onGetAndDecryptRecordStoreError(@NonNull String error) {
				hasStoreError = true;
				entry.processed = false;
				awaitCounter.done();
				taskQueue.done();
			}

			void invalid(String reason) {
				done.onPrivateBoxInvalidEntry(source, reason);
				awaitCounter.done();
				taskQueue.done();
			}
		}

		@Override
		public void onAwaitCounterDone() {
			isReading = false;
			if (hasStoreError) done.onPrivateBoxReadingFailed();
			else done.onPrivateBoxReadingDone();
		}
	}

	public class Entry {
		final Hash hash;
		boolean processed = false;

		Entry(Hash hash) {
			this.hash = hash;
		}
	}

	public interface ReadingDone {
		void onPrivateBoxEntry(@NonNull Source source, @NonNull Record envelope, @NonNull HashAndKey contentHashAndKey, @NonNull Record content);

		void onPrivateBoxInvalidEntry(@NonNull Source source, @NonNull String reason);

		void onPrivateBoxReadingDone();

		void onPrivateBoxReadingFailed();
	}
}
