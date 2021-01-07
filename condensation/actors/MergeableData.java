package condensation.actors;

import condensation.serialization.Record;
import condensation.stores.Store;

public interface MergeableData {
	void addDataTo(Record record);

	void mergeData(Record record);

	void mergeExternalData(Store store, Record record, Source source);
}
