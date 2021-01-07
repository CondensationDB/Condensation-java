package condensation.tools;

import java.util.HashSet;

import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.stores.Store;

public abstract class ObjectInspection extends LoadingInspection<ObjectInspection.Result> {
	public ObjectInspection(CondensationView view, KeyPair keyPair, Store store) {
		super(view, keyPair, store);
	}

	@Override
	int calculateResultLines() {
		if (result.record == null) return 2;
		return result.record.countEntries();
	}

	@Override
	void drawResult(Drawer drawer) {
		float right = drawer.width - view.style.gap;
		drawer.text(Misc.byteSize(result.byteLength), right, 0, view.style.rightGrayText);
		if (result.record == null) {
			drawer.text(result.start, view.style.monospaceText);
		} else {
			drawer.recordChildren(result.record, 0);
		}
	}

	@Override
	void addChildren() {
		if (result.record == null) return;
		traverseHashes(result.record, new HashSet<Hash>());
	}

	private void traverseHashes(Record record, HashSet<Hash> hashes) {
		if (record.hash != null && !hashes.contains(record.hash)) {
			hashes.add(record.hash);
			HashAndKey hashAndKey = record.asHashAndKey();
			if (hashAndKey != null)
				children.add(new EncryptedObjectInspection(view, keyPair, store, hashAndKey));
			else
				children.add(new PlainObjectInspection(view, keyPair, store, record.hash));
		}

		for (Record child : record.children)
			traverseHashes(child, hashes);
	}

	class Result {
		final long byteLength;
		final String start;
		final Record record;

		Result(CondensationObject object, Record record) {
			this.byteLength = object.byteLength();
			this.start = object.data.toString(30);
			this.record = record;
		}
	}

	void setTitle(String type, Hash hash) {
		title = type + " " + hash.shortHex();
	}
}
