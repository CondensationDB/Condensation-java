package condensation.examples.actorWithRecord;

import condensation.actors.MergeableData;
import condensation.actors.PrivateRoot;
import condensation.actors.Source;
import condensation.serialization.Bytes;
import condensation.serialization.Record;
import condensation.stores.Store;

class MostRecentRecord implements MergeableData {
	final PrivateRoot privateRoot;
	final Bytes label;

	// State
	long revision = 0L;
	Record record = new Record();

	MostRecentRecord(PrivateRoot privateRoot, Bytes label) {
		this.privateRoot = privateRoot;
		this.label = label;
		privateRoot.addDataHandler(label, this);
	}

	@Override
	public void addDataTo(Record record) {
		if (revision <= 0) return;
		record.addUnsigned(revision).add(record.children);
	}

	@Override
	public void mergeData(Record record) {
		for (Record child : record.children) {
			long revision = child.asUnsigned();
			if (revision <= this.revision) return;
			this.revision = revision;
			this.record = child;
		}
	}

	@Override
	public void mergeExternalData(Store store, Record record, Source source) {
		// Not implemented
	}
}
