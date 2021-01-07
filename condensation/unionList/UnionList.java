package condensation.unionList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.MergeableData;
import condensation.actors.PrivateRoot;
import condensation.actors.Source;
import condensation.actors.Unsaved;
import condensation.tasks.LazyAction;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.stores.GetAndDecryptRecord;
import condensation.stores.Store;
import condensation.tasks.AwaitCounter;
import condensation.tasks.BackgroundTask;

public abstract class UnionList<I extends Item> implements MergeableData {
	public final PrivateRoot privateRoot;
	public final Bytes label;
	protected final HashMap<Bytes, I> items = new HashMap<>();
	final HashMap<Hash, Part> parts = new HashMap<>();
	public final Unsaved unsaved;

	private boolean hasPartsToMerge = false;
	public Part changes;

	public UnionList(@NonNull PrivateRoot privateRoot, @NonNull Bytes label) {
		this.privateRoot = privateRoot;
		this.label = label;
		this.unsaved = new Unsaved(privateRoot.unsaved);
		changes = new Part();
		privateRoot.addDataHandler(label, this);
	}

	public Collection<I> items() {
		return items.values();
	}

	@NonNull
	protected abstract I createItem(@NonNull Bytes id);

	@NonNull
	public I getOrCreate(@NonNull Bytes id) {
		I item = items.get(id);
		if (item != null) return item;
		I newItem = createItem(id);
		items.put(id, newItem);
		return newItem;
	}

	protected abstract void forgetObsoleteItems();

	public void forget(Bytes id) {
		I item = items.get(id);
		if (item == null) return;
		item.part.count -= 1;
		items.remove(id);
	}

	public void forget(I item) {
		item.part.count -= 1;
		items.remove(item.id);
	}

	// *** Merging

	@Override
	public void addDataTo(Record record) {
		for (Part part : parts.values())
			record.add(part.hashAndKey);
	}

	@Override
	public void mergeData(Record record) {
		for (Record child : record.children) {
			HashAndKey hashAndKey = child.asHashAndKey();
			if (hashAndKey == null) continue;
			if (parts.containsKey(hashAndKey.hash)) continue;
			Part part = new Part();
			part.hashAndKey = hashAndKey;
			parts.put(hashAndKey.hash, part);
			hasPartsToMerge = true;
		}
	}

	@Override
	public void mergeExternalData(Store store, Record record, Source source) {
		// TODO
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
				// TODO: this is dangerous (see DataTree.Read)
				done.onUnionListReadDone();
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
				new GetAndDecryptRecord(part.hashAndKey, privateRoot.unsaved, privateRoot.privateBoxReader.keyPair, this);
			}

			@Override
			public void onGetAndDecryptRecordDone(@NonNull Record record, @NonNull CondensationObject object) {
				part.size = object.byteLength();
				part.loadedRecord = record;
				awaitCounter.done();
			}

			@Override
			public void onGetAndDecryptRecordInvalid(@NonNull String reason) {
				Condensation.log("UnionList: Part " + part.hashAndKey.hash.shortHex() + " is invalid (" + reason + ") and therefore removed.");
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
				done.onUnionListReadFailed();
				return;
			}

			// Merge the loaded parts
			for (Part part : parts.values()) {
				if (part.isMerged) continue;
				if (part.loadedRecord == null) continue;

				// Merge
				for (Record child : part.loadedRecord.children)
					mergeRecord(part, child);

				part.loadedRecord = null;
				part.isMerged = true;
			}

			// Wrap up
			done.onUnionListReadDone();
		}
	}

	protected abstract void mergeRecord(Part part, Record record);

	public interface ReadDone {
		void onUnionListReadDone();

		void onUnionListReadFailed();
	}

	// *** Change management

	public boolean hasChanges() {
		return changes.count > 0;
	}

	public final ArrayList<ChangeListener> changeListeners = new ArrayList<>();

	public LazyAction changeNotification = new LazyAction(10) {
		@Override
		protected void action() {
			for (ChangeListener listener : changeListeners)
				listener.onUnionListChanged();
		}
	};

	// *** Saving

	public void save(final SaveDone done) {
		if (unsaved.isSaving()) {
			Condensation.mainThread.post(new Runnable() {
				@Override
				public void run() {
					done.onUnionListSaveFailed();
				}
			});
			return;
		}

		forgetObsoleteItems();
		new Save(done);
	}

	class Save implements BackgroundTask {
		final SaveDone done;
		final Record record = new Record();

		Part newPart = null;
		CondensationObject newObject = null;
		HashAndKey newHashAndKey = null;
		ArrayList<HashAndKey> obsolete = new ArrayList<>();

		Save(SaveDone done) {
			this.done = done;
			unsaved.startSaving();

			if (changes.count == 0) {
				findObsoleteParts();
				wrapUp();
			} else {
				writeChanges();
			}
		}

		void findObsoleteParts() {
			for (Part part : parts.values())
				if (part.isMerged && part.count == 0) obsolete.add(part.hashAndKey);
		}

		void writeChanges() {
			// Take the changes
			newPart = changes;
			changes = new Part();

			// Add all changes
			for (I item : items.values()) {
				if (item.part != newPart) continue;
				item.addToRecord(record);
			}

			// Select all parts smaller than 2 * count elements
			int count = newPart.count;
			while (true) {
				boolean addedPart = false;
				for (Part part : parts.values()) {
					if (!part.isMerged || part.selected || part.count >= count * 2) continue;
					count += part.count;
					part.selected = true;
					addedPart = true;
				}

				if (!addedPart) break;
			}

			// Include the selected items
			for (I item : items.values()) {
				if (!item.part.selected) continue;
				item.addToRecord(record);
				item.setPart(newPart);
			}

			// Serialize the new part
			findObsoleteParts();
			Condensation.computationExecutor.run(this);
		}

		@Override
		public void background() {
			newObject = record.toObject();
			Bytes key = newObject.cryptInplace();
			Hash hash = newObject.calculateHash();
			newHashAndKey = new HashAndKey(hash, key);
		}

		@Override
		public void after() {
			newPart.hashAndKey = newHashAndKey;
			newPart.isMerged = true;
			parts.put(newHashAndKey.hash, newPart);
			privateRoot.unsaved.state.addObject(newHashAndKey.hash, newObject);
			wrapUp();
		}

		void wrapUp() {
			// Remove obsolete parts
			for (HashAndKey hashAndKey : obsolete)
				parts.remove(hashAndKey.hash);

			// Propagate the unsaved state
			privateRoot.unsaved.state.merge(unsaved.savingState);
			privateRoot.dataChanged();
			unsaved.savingDone();
			done.onUnionListSaveDone();
		}
	}

	public interface SaveDone {
		void onUnionListSaveDone();

		void onUnionListSaveFailed();
	}
}

