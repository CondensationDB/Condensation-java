package condensation.actors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.stores.BoxAddition;
import condensation.stores.BoxLabel;
import condensation.stores.BoxRemoval;
import condensation.stores.MissingObject;
import condensation.stores.Store;
import condensation.stores.Transfer;

public class PrivateRoot {
	public final PrivateBoxReader privateBoxReader;
	public final Unsaved unsaved;
	private final HashMap<Bytes, MergeableData> dataHandlers = new HashMap<>();

	// State
	private long procured = 0;
	private boolean hasChanges = false;
	private HashSet<Hash> mergedEntries = new HashSet<>();

	public PrivateRoot(KeyPair keyPair, Store store) {
		privateBoxReader = new PrivateBoxReader(keyPair, store);
		unsaved = new Unsaved(store);
	}

	public void addDataHandler(Bytes label, MergeableData dataHandler) {
		dataHandlers.put(label, dataHandler);
	}

	public void removeDataHandler(Bytes label, MergeableData dataHandler) {
		MergeableData registered = dataHandlers.get(label);
		if (registered != dataHandler) return;
		dataHandlers.remove(label);
	}

	// *** Procurement

	public void procure(long interval, ProcureDone done) {
		new Procure(interval, done);
	}

	class Procure implements PrivateBoxReader.ReadingDone {
		final ProcureDone done;
		final long now = System.currentTimeMillis();

		Procure(long interval, ProcureDone done) {
			this.done = done;
			if (procured + interval > now) {
				done.onPrivateRootProcureDone();
				return;
			}

			privateBoxReader.read(this);
		}

		@Override
		public void onPrivateBoxEntry(@NonNull Source source, @NonNull Record envelope, @NonNull HashAndKey contentHashAndKey, @NonNull Record content) {
			for (Record section : content.children) {
				MergeableData dataHandler = dataHandlers.get(section.bytes);
				if (dataHandler == null) continue;
				dataHandler.mergeData(section);
			}

			mergedEntries.add(source.hash);
		}

		@Override
		public void onPrivateBoxInvalidEntry(@NonNull Source source, @NonNull String reason) {
			done.onPrivateRootProcureInvalidEntry(source, reason);
			source.discard();
		}

		@Override
		public void onPrivateBoxReadingDone() {
			procured = now;
			done.onPrivateRootProcureDone();
		}

		@Override
		public void onPrivateBoxReadingFailed() {
			done.onPrivateRootProcureFailed();
		}
	}

	// *** Saving

	public void dataChanged() {
		hasChanges = true;
	}

	public boolean hasChanges() {
		return hasChanges;
	}

	public boolean isSaving() {
		return unsaved.isSaving();
	}

	public void save(EntrustedKeysProvider entrustedKeysProvider, final SavingDone done) {
		if (unsaved.isSaving()) {
			Condensation.mainThread.post(new Runnable() {
				@Override
				public void run() {
					done.onPrivateRootSavingFailed();
				}
			});
			return;
		}

		new Save(entrustedKeysProvider, done);
	}

	class Save implements EntrustedKeysProvider.Done, Transfer.Done, Store.ModifyDone, Runnable {
		final SavingDone done;

		// State
		BoxAddition addition;
		final ArrayList<BoxRemoval> removals = new ArrayList<>();

		Save(EntrustedKeysProvider entrustedKeysProvider, SavingDone done) {
			this.done = done;
			entrustedKeysProvider.getEntrustedKeys(this);
		}

		@Override
		public void onGetEntrustedKeysDone(Iterable<PublicKey> entrustedKeys) {
			unsaved.startSaving();

			if (!hasChanges) {
				Condensation.mainThread.post(this);
				return;
			}

			hasChanges = false;

			// Create the record
			Record record = new Record();
			record.add(condensation.dataTree.BC.created).add(System.currentTimeMillis());
			record.add(condensation.dataTree.BC.client).add(Condensation.versionBytes);
			for (Map.Entry<Bytes, MergeableData> entry : dataHandlers.entrySet())
				entry.getValue().addDataTo(record.add(entry.getKey()));

			// Submit the object
			CondensationObject object = record.toObject();
			Bytes key = object.cryptInplace();
			Hash hash = object.calculateHash();
			unsaved.savingState.addObject(hash, object);
			HashAndKey hashAndKey = new HashAndKey(hash, key);

			// Create the envelope
			ArrayList<PublicKey> publicKeys = new ArrayList<>();
			publicKeys.add(privateBoxReader.keyPair.publicKey);
			for (PublicKey entrustedKey : entrustedKeys)
				publicKeys.add(entrustedKey);
			CondensationObject envelopeObject = privateBoxReader.keyPair.createPrivateEnvelope(hashAndKey, publicKeys).toObject();
			Hash envelopeHash = envelopeObject.calculateHash();

			// Prepare the addition and the removals
			addition = new BoxAddition(privateBoxReader.keyPair.publicKey.hash, BoxLabel.PRIVATE, envelopeHash, envelopeObject);
			for (Hash mergedHash : mergedEntries)
				removals.add(new BoxRemoval(privateBoxReader.actorOnStore.publicKey.hash, BoxLabel.PRIVATE, mergedHash));

			// Transfer the tree
			privateBoxReader.keyPair.transfer(Collections.singleton(hash), unsaved, privateBoxReader.actorOnStore.store, this);
		}

		@Override
		public void onGetEntrustedKeysFailed() {
			done.onPrivateRootSavingFailed();
		}

		@Override
		public void onTransferDone() {
			// Modify the private box
			privateBoxReader.actorOnStore.store.modify(Collections.singleton(addition), removals, privateBoxReader.keyPair, this);
		}

		@Override
		public void onTransferMissingObject(@NonNull MissingObject missingObject) {
			missingObject.context = "Private root saving";
			missingObject.report();
			savingFailed();
		}

		@Override
		public void onTransferStoreError(@NonNull Store store, @NonNull String error) {
			Condensation.log("Private root transfer failed " + error);
			savingFailed();
		}

		@Override
		public void onModifyDone() {
			mergedEntries.clear();
			mergedEntries.add(addition.hash);
			savingSucceeded();
		}

		@Override
		public void onModifyStoreError(@NonNull String error) {
			Condensation.log("Private root modify error " + error);
			savingFailed();
		}

		@Override
		public void run() {
			savingSucceeded();
		}

		void savingSucceeded() {
			// Discard all merged sources
			for (Source source : unsaved.savingState.mergedSources)
				source.discard();

			// Call all data saved handlers
			for (DataSavedHandler handler : unsaved.savingState.dataSavedHandlers)
				handler.onDataSaved();

			unsaved.savingDone();
			done.onPrivateRootSavingDone();
		}

		void savingFailed() {
			hasChanges = true;
			done.onPrivateRootSavingFailed();
			unsaved.savingFailed();
		}
	}

	public interface SavingDone {
		void onPrivateRootSavingDone();

		void onPrivateRootSavingFailed();
	}

	public interface ProcureDone {
		void onPrivateRootProcureDone();

		void onPrivateRootProcureInvalidEntry(@NonNull Source source, @NonNull String reason);

		void onPrivateRootProcureFailed();
	}
}
