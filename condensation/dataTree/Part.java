package condensation.dataTree;

import condensation.Condensation;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;

public class Part {
	boolean isMerged = false;
	public HashAndKey hashAndKey = null;
	int size = 0;

	// Values used from this part
	Item firstValue = null;
	int count = 0;

	// Used for reading and merging
	Record loadedRecord = null;
	boolean hasError = false;

	// *** Integrity check

	public void check() {
		if (hashAndKey == null) {
			Condensation.log("DataTree.Check |  Changed values " + this.count);
		} else {
			Condensation.log("DataTree.Check |  Part " + hashAndKey.hash.shortHex() + " values " + this.count);
		}

		int countValues = 0;
		for (Item item = firstValue; item != null; item = item.nextInPart) {
			Condensation.log("DataTree.Check |    Value " + item.selector.toString() + " " + item.selector.isSet());
			countValues += 1;
			if (item.revision <= 0L)
				Condensation.log("DataTree.Check |      VALUE ITEM WITH 0 REVISION");
			if (item.dataTree.itemsBySelector.get(item.selector) == null)
				Condensation.log("DataTree.Check |      VALUE ITEM NOT IN DATATREE");
		}

		if (countValues != this.count)
			Condensation.log("DataTree.Check     WRONG VALUE COUNT " + countValues + " != " + this.count);
	}
}
