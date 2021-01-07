package condensation.dataTree;

import java.util.ArrayList;

import condensation.Condensation;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.tasks.BackgroundTask;

// Saves a DataTree.
// If items have changed, a new part is written to the store.
// In any case, the new active parts are written to the backend, and inactive parts are removed.
class Save implements BackgroundTask {
	final DataTree dataTree;
	final DataTree.SaveDone done;

	// Result
	public long revision = 0L;
	public ArrayList<Part> obsoleteParts = new ArrayList<>();                      // parts that have become obsolete

	// The new part, if any
	public Part newPart = null;
	public CondensationObject newObject = null;
	public HashAndKey newHashAndKey = null;

	Save(DataTree dataTree, DataTree.SaveDone done) {
		this.dataTree = dataTree;
		this.done = done;

		dataTree.unsaved.startSaving();
		if (dataTree.changes.count == 0) {
			findObsoleteParts();
			wrapUp();
		} else {
			writeChanges();
		}
	}

	void findObsoleteParts() {
		for (Part part : dataTree.parts.values())
			if (part.isMerged && part.count == 0) obsoleteParts.add(part);
	}

	void writeChanges() {
		// Prepare
		revision = System.currentTimeMillis();

		// Take the changes
		newPart = dataTree.changes;
		dataTree.changes = new Part();

		// Include all parts smaller than 2*C
		int count = newPart.count;
		while (true) {
			boolean addedPart = false;
			for (Part part : dataTree.parts.values()) {
				//CN.log("SaveDataTree -- part " + part.hashAndKey.hash.shortHex() + " ismerged " + part.isMerged + " count " + part.count + " taking " + taking);
				if (!part.isMerged || part.count == 0 || part.count >= count * 2) continue;

				count += part.count;
				addedPart = true;

				Item item = part.firstValue;
				while (item != null) {
					Item nextItem = item.nextInPart;
					item.setPart(newPart);
					item = nextItem;
				}
			}

			if (!addedPart) break;
		}

		// Find obsolete parts
		findObsoleteParts();

		// Create the record to save
		for (Item item = newPart.firstValue; item != null; item = item.nextInPart)
			item.createSaveRecord(newPart);

		Record record = new Record();
		record.add(BC.created).add(revision);
		record.add(BC.client).add(Condensation.versionBytes);
		record.add(dataTree.rootItem().createSaveRecord(newPart));

		// Detach the save records
		for (Item item = newPart.firstValue; item != null; item = item.nextInPart)
			item.detachSaveRecord();

		// Serialize and encrypt the record
		newObject = record.toObject();
		Condensation.computationExecutor.run(this);
	}

	private void logParts(Part part) {
		for (Item item = part.firstValue; item != null; item = item.nextInPart)
			Condensation.log("SaveDataTree changed value " + item.selector.toString() + " rev " + item.revision);
	}

	@Override
	public void background() {
		Bytes key = newObject.cryptInplace();
		Hash hash = newObject.calculateHash();
		newHashAndKey = new HashAndKey(hash, key);
	}

	@Override
	public void after() {
		newPart.hashAndKey = newHashAndKey;
		newPart.isMerged = true;
		dataTree.parts.put(newHashAndKey.hash, newPart);
		dataTree.unsaved.savingState.addObject(newHashAndKey.hash, newObject);
		wrapUp();
	}

	void wrapUp() {
		// Remove obsolete parts
		for (Part part : obsoleteParts)
			dataTree.parts.remove(part.hashAndKey.hash);

		// Commit
		dataTree.savingDone(revision, newPart, obsoleteParts);

		// Notify
		done.onDataTreeSaveDone();
	}
}
