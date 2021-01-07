package condensation.dataTree;

import androidx.annotation.NonNull;

import condensation.Condensation;
import condensation.ImmutableStack;
import condensation.serialization.Bytes;
import condensation.serialization.Record;

// An item is a temporary structure. Do not keep references to it, but use selectors to access data.
final class Item implements Comparable<Item> {
	// Base information
	final DataTree dataTree;
	final Selector selector;

	// Listeners
	final static ImmutableStack<BranchListener> noBranchListeners = new ImmutableStack<>();
	final static ImmutableStack<ValueListener> noValueListeners = new ImmutableStack<>();
	ImmutableStack<BranchListener> branchListeners = noBranchListeners;
	ImmutableStack<ValueListener> valueListeners = noValueListeners;

	// Notification
	final static byte notifyValueParent = 1;
	final static byte notifyValueItem = 2;
	final static byte notifyValue = notifyValueParent | notifyValueItem;
	final static byte notifyPrune = 4;
	final static byte notifyParentMask = notifyValueParent | notifyPrune;
	byte notifyFlags = 0;
	Item notifyChild = null;
	Item notifySibling = null;

	// Tree
	// Except for the root item, all items must have a parent, and must be linked in parent.firstChild.
	// countChildren must reflect the number of items links through firstChild.
	final Item parent;
	int countChildren = 0;
	Item firstChild = null;
	Item prevSibling = null;
	Item nextSibling = null;

	// Part from which the newest revision of the data was loaded
	// revision  part
	// no        no         This item does not have a values.
	// yes       no         Invalid state.
	// yes       changes    The value have been changed locally, but not saved yet. The item must be linked in dataTree.firstChanged.
	// yes       yes        The value have been loaded from a part, or saved to a part. The item must be linked in valuePart.firstValue.
	// no        yes        Invalid state.
	Part part;
	Item prevInPart = null;
	Item nextInPart = null;

	// Value (record with revision), record.children.size() == 0 means no value
	// revision  value
	// no        no         This item does not have a value, and has never had a value. This is the default state of an item. This state is never saved to disk, and the item may be pruned from the tree.
	// yes       no         This item currently does not have a value, but might have had a value in the past. The value was deleted at T_revision.
	// yes       yes        This item currently has a value. It was last modified at T_revision.
	// no        yes        Invalid state.
	long revision = 0L;
	@NonNull
	Record record = new Record();

	// Constructor

	Item(DataTree dataTree, Selector selector) {
		this.dataTree = dataTree;
		this.selector = selector;

		if (selector.parent == null) {
			// Root items
			this.parent = null;
		} else {
			// Any other item
			this.parent = dataTree.getOrCreate(selector.parent);

			// Add this item to the tree
			nextSibling = parent.firstChild;
			if (nextSibling != null) nextSibling.prevSibling = this;
			parent.firstChild = this;
			parent.countChildren += 1;

			// Mark it for pruning
			notify(notifyPrune);
		}
	}

	// Notification

	// notifyFlags must be notifyValue, notifyPrune, or notifyValue | notifyPrune.
	void notify(int notifyFlags) {
		if ((this.notifyFlags & notifyFlags) == notifyFlags) return;

		if (this.notifyFlags == 0) {
			if (dataTree.notifier.count == 0)
				Condensation.mainThread.postDelayed(dataTree.notifier, 10);

			dataTree.notifier.count += 1;
			if (parent != null) {
				notifySibling = parent.notifyChild;
				parent.notifyChild = this;
			}
		}

		this.notifyFlags |= notifyFlags;
		if (parent != null) parent.notify(notifyFlags & notifyParentMask);
	}

	// Item tree

	Iterable<Item> children() {
		return new ItemIterable(new ChildIterator(this));
	}

	void pruneIfPossible() {
		// Don't remove items with children
		if (firstChild != null) return;

		// Don't remove if the item has notifications or listeners
		if (notifyFlags > 0) return;
		if (valueListeners.length > 0) return;
		if (branchListeners.length > 0) return;

		// Don't remove if the item has a value
		if (revision > 0L) return;

		// Don't remove the root item
		if (parent == null) return;

		// Remove this from the tree
		if (prevSibling == null) parent.firstChild = nextSibling;
		else prevSibling.nextSibling = nextSibling;
		if (nextSibling != null) nextSibling.prevSibling = prevSibling;
		nextSibling = null;
		prevSibling = null;
		parent.countChildren -= 1;

		// Remove this from the datatree hash
		dataTree.itemsBySelector.remove(selector);
	}

	// Low-level part change

	void setPart(@NonNull Part part) {
		removePart();
		this.part = part;
		nextInPart = this.part.firstValue;
		if (nextInPart != null) nextInPart.prevInPart = this;
		this.part.firstValue = this;
		this.part.count += 1;
	}

	private void removePart() {
		if (part == null) return;
		if (part.firstValue == this) part.firstValue = nextInPart;
		if (nextInPart != null) nextInPart.prevInPart = prevInPart;
		if (prevInPart != null) prevInPart.nextInPart = nextInPart;
		prevInPart = null;
		nextInPart = null;
		part.count -= 1;
		part = null;
	}

	// Merge a value, either from an existing part, or as a local change

	boolean mergeValue(Part part, long revision, @NonNull Record record) {
		if (revision <= 0) return false;
		if (revision < this.revision) return false;
		if (revision == this.revision && part.size <= this.part.size) return false;

		this.record = record;
		setPart(part);

		if (revision == this.revision) return true;
		this.revision = revision;
		notify(notifyValue);
		//check();
		dataTree.dataChanged();
		return true;
	}

	void forget() {
		if (revision <= 0) return;
		revision = 0;
		record = new Record();
		removePart();
		notify(notifyValue | notifyPrune);
		//check();
	}

	// General

	@Override
	public int compareTo(@NonNull Item that) {
		return selector.compareTo(that.selector);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(selector.label);
		if (revision > 0) builder.append(" +").append(revision);
		builder.append(" [").append(record).append("]");
		return builder.toString();
	}

	public void log(String prefix) {
		Condensation.log("DataTree.Log " + prefix + toString());
		String childPrefix = prefix + "  ";
		for (Item child = firstChild; child != null; child = child.nextSibling)
			child.log(childPrefix);
	}

	void check() {
		//Condensation.log("DataTree.Check |  rev " + (revision > 0 ? "+" + revision : "-") + " sel " + selector + (isActive() ? " ++" : " --"));
		if (!dataTree.itemsBySelector.containsKey(selector))
			Condensation.logError("DataTree.Check |    NOT IN HASH TABLE " + toString());

		// Tree

		if (nextSibling != null && nextSibling.prevSibling != this)
			Condensation.logError("DataTree.Check |    nextSibling " + toString());

		if (prevSibling != null && prevSibling.nextSibling != this)
			Condensation.logError("DataTree.Check |    prevSibling " + toString());

		if (!check_isChild())
			Condensation.logError("DataTree.Check |    NOT CHILD OF PARENT " + toString());

		//if (!check_isDescendant())
		//    Condensation.log("DataTree.Check |    NOT DESCENDANT OF ROOT");

		for (Item item : children())
			if (item.parent != this)
				Condensation.logError("DataTree.Check |    CUCKOO CHILD " + toString());

		// Values

		boolean hasRevision = revision > 0L;

		if (!check_isInValueList())
			Condensation.logError("DataTree.Check |    NOT IN VALUE LIST " + toString());

		if (part != null && !hasRevision)
			Condensation.logError("DataTree.Check |    LIST, BUT NO REVISION " + toString());

		if (part == null && hasRevision)
			Condensation.logError("DataTree.Check |    REVISION, BUT NO LIST " + toString());

		if (nextInPart != null && nextInPart.prevInPart != this)
			Condensation.logError("DataTree.Check |    nextInValueList " + toString());

		if (prevInPart != null && prevInPart.nextInPart != this)
			Condensation.logError("DataTree.Check |    prevInValueList " + toString());

		if (!hasRevision && !record.children.isEmpty())
			Condensation.logError("DataTree.Check |    HAS VALUES, BUT NO REVISION " + toString());
	}

	boolean check_isChild() {
		if (parent == null) return true;
		for (Item item : parent.children())
			if (item == this) return true;
		return false;
	}

	boolean check_isInValueList() {
		if (part == null) return true;
		for (Item item = part.firstValue; item != null; item = item.nextInPart)
			if (item == this) return true;
		return false;
	}

	// *** Saving

	Record saveRecord = null;

	Record createSaveRecord(Part savingPart) {
		if (saveRecord != null) return saveRecord;
		saveRecord = parent == null ? new Record(BC.root) : parent.createSaveRecord(savingPart).add(selector.label);
		if (part == savingPart) {
			if (revision <= 0L) Condensation.logError("Item saving zero revision of " + selector.toString());
			saveRecord.add(revision).add(record.children);
		} else {
			saveRecord.add(Bytes.empty, null);
		}
		return saveRecord;
	}

	void detachSaveRecord() {
		if (saveRecord == null) return;
		saveRecord = null;
		if (parent != null) parent.detachSaveRecord();
	}
}
