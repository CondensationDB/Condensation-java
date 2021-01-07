package condensation.unionList;

import condensation.serialization.HashAndKey;
import condensation.serialization.Record;

public class Part {
	boolean isMerged = false;
	HashAndKey hashAndKey = null;
	public long size = 0;
	int count = 0;

	// Used for reading and merging
	Record loadedRecord = null;

	// Used while saving
	boolean selected;
}
