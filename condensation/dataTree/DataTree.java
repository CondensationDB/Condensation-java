package condensation.dataTree;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.KeyPair;
import condensation.actors.Unsaved;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.stores.GetAndDecryptRecord;
import condensation.stores.Store;
import condensation.tasks.AwaitCounter;

// This class is optimized for rather small data sets, i.e. a few 1000 entries max. It reads and deserializes all data into memory, and notifies listeners upon change.
// With more elements, it would be preferable to deserialize on demand.
public abstract class DataTree {
	// Configuration
	public final KeyPair keyPair;
	public final Unsaved unsaved;

	// Root selector
	public final Selector root = new Selector(this);

	// State
	final HashMap<Selector, Item> itemsBySelector = new HashMap<>();
	protected final HashMap<Hash, Part> parts = new HashMap<>();
	private boolean hasPartsToMerge = false;
	Part changes = new Part();

	// TODO: remove auto saving from here
	public DataTree(KeyPair keyPair, Store store) {
		this.keyPair = keyPair;
		this.unsaved = new Unsaved(store);
	}

	@Override
	public String toString() {
		return "DataTree";
	}

	// *** Items

	Item get(Selector selector) {
		return itemsBySelector.get(selector);
	}

	Item getOrCreate(Selector selector) {
		if (selector == null) return null;
		Item item = itemsBySelector.get(selector);
		if (item != null) return item;
		Item newItem = new Item(this, selector);
		itemsBySelector.put(selector, newItem);
		return newItem;
	}

	Item rootItem() {
		return getOrCreate(root);
	}

	// *** Merging

	// TODO: perhaps we should accept Sources and DataSaveHandlers here (?), to be propagated when the merge is done
	public void merge(ArrayList<HashAndKey> hashesAndKeys) {
		for (HashAndKey hashAndKey : hashesAndKeys) {
			if (hashAndKey == null) continue;
			if (parts.containsKey(hashAndKey.hash)) continue;
			Part part = new Part();
			part.hashAndKey = hashAndKey;
			parts.put(hashAndKey.hash, part);
			hasPartsToMerge = true;
		}
	}

	public boolean hasPartsToMerge() {
		return hasPartsToMerge;
	}

	public void read(ReadDone done) {
		new Read(done);
	}

	class Read implements AwaitCounter.Done {
		final ReadDone done;
		final AwaitCounter awaitCounter = new AwaitCounter();
		boolean hasError = false;

		Read(ReadDone done) {
			this.done = done;

			if (!hasPartsToMerge) {
				// TODO: this is dangerous, since another read operation could be ongoing, and the data not actually be available yet
				// the ReadDone event should be called when the data is ready
				// or concurrent reading should not be possible
				done.onDataTreeReadDone();
				return;
			}

			hasPartsToMerge = false;
			for (Part part : parts.values()) {
				if (part.isMerged) continue;
				if (part.loadedRecord != null) continue;
				new LoadPart(part);
			}

			awaitCounter.then(this);
		}

		class LoadPart implements GetAndDecryptRecord.Done {
			final Part part;

			LoadPart(Part part) {
				this.part = part;
				awaitCounter.await();

				// Get the record
				new GetAndDecryptRecord(part.hashAndKey, unsaved, keyPair, this);
			}

			@Override
			public void onGetAndDecryptRecordDone(@NonNull Record record, @NonNull CondensationObject object) {
				part.size = object.byteLength();
				part.loadedRecord = record;
				awaitCounter.done();
			}

			@Override
			public void onGetAndDecryptRecordInvalid(@NonNull String reason) {
				Condensation.log("DataTree: Part " + part.hashAndKey.hash.shortHex() + " is invalid (" + reason + ") and therefore removed.");
				parts.remove(part.hashAndKey.hash);
				awaitCounter.done();
			}

			@Override
			public void onGetAndDecryptRecordStoreError(@NonNull String error) {
				hasError = true;
				awaitCounter.done();
			}
		}

		@Override
		public void onAwaitCounterDone() {
			if (hasError) {
				hasPartsToMerge = true;
				done.onDataTreeReadFailed();
				return;
			}

			// Merge the loaded parts
			for (Part part : parts.values()) {
				if (part.isMerged) continue;
				if (part.loadedRecord == null) continue;
				boolean oldFormat = part.loadedRecord.child(BC.client).textValue().contains("2018");
				mergeNode(part, root, part.loadedRecord.child(BC.root), oldFormat);
				part.loadedRecord = null;
				part.isMerged = true;
			}

			// Wrap up
			done.onDataTreeReadDone();
		}

		void mergeNode(Part part, Selector selector, Record record, boolean oldFormat) {
			// Prepare
			int count = record.children.size();
			if (count < 1) return;
			Item item = getOrCreate(selector);

			// Merge value
			Record valueRecord = record.firstChild();
			if (oldFormat) valueRecord = valueRecord.firstChild();
			item.mergeValue(part, valueRecord.asInteger(), valueRecord);

			// Iteratively merge children
			for (int i = 1; i < count; i++) {
				Record child = record.children.get(i);
				mergeNode(part, selector.child(child.bytes), child, oldFormat);
			}
		}
	}

	public interface ReadDone {
		void onDataTreeReadDone();

		void onDataTreeReadFailed();
	}

	// *** Saving

	// This is called by the items whenever some data changes.
	final void dataChanged() {
		// We don't need that now
		// It is very similar (the same?) to a BranchListener on dataTree.root
	}

	public boolean hasChanges() {
		return changes.count > 0;
	}

	public void save(final SaveDone done) {
		if (unsaved.isSaving()) {
			Condensation.mainThread.post(new Runnable() {
				@Override
				public void run() {
					done.onDataTreeSaveFailed();
				}
			});
			return;
		}

		new Save(DataTree.this, done);
	}

	// This is called by SaveDataTree to wrap up saving.
	// The implementation must call unsavedState.savingDone() or unsavedState.propagateTo(UnsavedState).
	public abstract void savingDone(long revision, Part newPart, ArrayList<Part> obsoleteParts);

	public interface SaveDone {
		void onDataTreeSaveDone();

		void onDataTreeSaveFailed();
	}

	public static final SaveDone ignoreSaveDone = new SaveDone() {
		@Override
		public void onDataTreeSaveDone() {
		}

		@Override
		public void onDataTreeSaveFailed() {
		}
	};

	// *** Notification

	final Notifier notifier = new Notifier(this);

	public void log() {
		Condensation.log("DataTree.Log ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		getOrCreate(root).log("|");
		Condensation.log("DataTree.Log ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
	}

	public void checkStructure() {
		try {
			Condensation.log("DataTree.Check ========================================================================");
			Condensation.log("DataTree.Check items by selector hash table (" + itemsBySelector.size() + ")");
			for (Item item : itemsBySelector.values()) item.check();

			changes.check();

			Condensation.log("DataTree.Check parts");
			for (Part part : parts.values()) part.check();
			Condensation.log("DataTree.Check ========================================================================");
		} catch (Throwable t) {
			Condensation.logError("DataTree.Check exception", t);
		}
	}
}
