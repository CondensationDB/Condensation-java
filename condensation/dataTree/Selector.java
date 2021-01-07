package condensation.dataTree;

import androidx.annotation.NonNull;

import condensation.ImmutableList;
import condensation.ImmutableStack;
import condensation.actors.DataSavedHandler;
import condensation.actors.Source;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;

// A selector points to a single item in a (specific) data tree, and provides methods to query and modify the item.
public final class Selector implements Comparable<Selector> {
	public static ImmutableStack<Selector> none = new ImmutableStack<>();

	public final DataTree dataTree;
	public final Selector parent;       // null for the root selector only
	public final Bytes label;

	// Precalculated for improved performance
	public final int depth;
	public final int hashCode;

	// Root selector constructor
	Selector(DataTree dataTree) {
		this.dataTree = dataTree;
		this.parent = null;
		this.label = Bytes.empty;
		this.depth = 0;
		this.hashCode = 0;
	}

	// Non-root selector constructor
	Selector(@NonNull Selector parent, @NonNull Bytes label) {
		this.dataTree = parent.dataTree;
		this.parent = parent;
		this.label = label;
		this.depth = parent.depth + 1;
		this.hashCode = parent.hashCode() ^ label.hashCode();
	}

	public Selector child(@NonNull Bytes segment) {
		return new Selector(this, segment);
	}

	public Selector child(@NonNull String text) {
		return child(Bytes.fromText(text));
	}

	public boolean isAncestorOf(@NonNull Selector child) {
		Selector selector = child.parent;
		while (selector != null) {
			if (equals(selector)) return true;
			selector = selector.parent;
		}
		return false;
	}

	public Selector childHolding(Selector selector) {
		while (selector.depth > depth + 1) selector = selector.parent;
		return selector.parent.equals(this) ? selector : null;
	}

	public SelectorRange any() {
		return new SelectorRange(this, SelectorRange.any);
	}

	public SelectorRange some(SelectorRange.Constraint constraint) {
		return new SelectorRange(this, constraint);
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof Selector && equals((Selector) that);
	}

	public boolean equals(Selector that) {
		if (that == null) return false;
		if (that == this) return true;
		if (dataTree != that.dataTree) return false;
		if (hashCode != that.hashCode) return false;
		if (depth != that.depth) return false;

		if (parent == null) return that.parent == null;
		else return label.equals(that.label) && parent.equals(that.parent);
	}

	public static boolean equals(Selector a, Selector b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		return a.equals(b);
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public int compareTo(@NonNull Selector that) {
		if (parent == null && that.parent == null) return 0;
		if (parent == null) return -1;
		if (that.parent == null) return 1;

		if (depth < that.depth) {
			int cmp = compareTo(that.parent);
			return cmp == 0 ? -1 : cmp;
		}

		if (depth > that.depth) {
			int cmp = parent.compareTo(that);
			return cmp == 0 ? 1 : cmp;
		}

		int cmp = parent.compareTo(that.parent);
		return cmp == 0 ? label.compareTo(that.label) : cmp;
	}

	@Override
	public String toString() {
		return parent == null ? dataTree.toString() + ":" : parent.toString() + "/" + label.toString(32);
	}

	public int countChildren() {
		Item item = dataTree.get(this);
		return item == null ? 0 : item.countChildren;
	}

	public ImmutableList<Selector> children() {
		Item item = dataTree.get(this);
		if (item == null) return new ImmutableList<>();
		Selector[] children = new Selector[item.countChildren];
		int i = 0;
		for (Item child = item.firstChild; child != null; child = child.nextSibling)
			children[i++] = child.selector;
		return new ImmutableList<>(children, 0, i);
	}

	public ChildIterator childIterator() {
		Item item = dataTree.get(this);
		return new ChildIterator(item);
	}

	// *** Value

	public long revision() {
		Item item = dataTree.get(this);
		return item == null ? 0L : item.revision;
	}

	public boolean isSet() {
		Item item = dataTree.get(this);
		return item != null && item.record.children.size() > 0;
	}

	public Record record() {
		Item item = dataTree.get(this);
		return item == null ? new Record() : item.record;
	}

	public boolean set(@NonNull Record record) {
		Item item = dataTree.getOrCreate(this);
		return item.mergeValue(dataTree.changes, Math.max(System.currentTimeMillis(), item.revision + 1), record);
	}

	public boolean merge(long revision, @NonNull Record record) {
		Item item = dataTree.getOrCreate(this);
		return item.mergeValue(dataTree.changes, revision, record);
	}

	public boolean merge(@NonNull Selector selector) {
		return merge(selector.revision(), selector.record().deepClone());
	}

	public void clear() {
		set(new Record());
	}

	public void clearInThePast() {
		if (isSet()) merge(revision() + 1, new Record());
	}

	public void forget() {
		Item item = dataTree.get(this);
		if (item == null) return;
		item.forget();
	}

	public void forgetBranch() {
		for (Selector child : children()) child.forgetBranch();
		forget();
	}

	// *** Notification ***

	public void trackBranch(BranchListener listener) {
		Item item = dataTree.getOrCreate(this);
		item.branchListeners = item.branchListeners.with(listener);
	}

	public void untrackBranch(BranchListener listener) {
		Item item = dataTree.get(this);
		if (item == null) return;
		item.branchListeners = item.branchListeners.without(listener);
		item.notify(Item.notifyPrune);
	}

	public void trackValue(ValueListener listener) {
		Item item = dataTree.getOrCreate(this);
		item.valueListeners = item.valueListeners.with(listener);
	}

	public void untrackValue(ValueListener listener) {
		Item item = dataTree.get(this);
		if (item == null) return;
		item.valueListeners = item.valueListeners.without(listener);
		item.notify(Item.notifyPrune);
	}

	// *** Convenience methods (simple interface) ***

	public Record firstValue() {
		Item item = dataTree.get(this);
		return item == null ? new Record() : item.record.firstChild();
	}

	public Bytes bytesValue() {
		return firstValue().bytes;
	}

	public Hash hashValue() {
		return firstValue().hash;
	}

	public String textValue() {
		return firstValue().bytes.asText();
	}

	public boolean booleanValue() {
		return firstValue().bytes.asBoolean();
	}

	public long integerValue() {
		return firstValue().bytes.asInteger();
	}

	public long unsignedValue() {
		return firstValue().bytes.asUnsigned();
	}

	public HashAndKey hashAndKeyValue() {
		return firstValue().asHashAndKey();
	}

	// Sets a new value
	public void set(Bytes bytes, Hash hash) {
		Record record = new Record();
		record.add(bytes, hash);
		set(record);
	}

	public void set(Bytes bytes) {
		set(bytes, null);
	}

	public void set(Hash value) {
		set(Bytes.empty, value);
	}

	public void set(String value) {
		set(Bytes.fromText(value), null);
	}

	public void set(String value, Hash hash) {
		set(Bytes.fromText(value), hash);
	}

	public void set(boolean value) {
		set(Bytes.fromBoolean(value), null);
	}

	public void set(boolean value, Hash hash) {
		set(Bytes.fromBoolean(value), hash);
	}

	public void set(long value) {
		set(Bytes.fromInteger(value), null);
	}

	public void set(long value, Hash hash) {
		set(Bytes.fromInteger(value), hash);
	}

	public void setUnsigned(long value) {
		set(Bytes.fromUnsigned(value), null);
	}

	public void setUnsigned(long value, Hash hash) {
		set(Bytes.fromUnsigned(value), hash);
	}

	public void set(HashAndKey value) {
		set(value.key, value.hash);
	}

	// *** Adding objects, merged sources, and data saved handlers ***

	public void addObject(Hash hash, CondensationObject object) {
		dataTree.unsaved.state.addObject(hash, object);
	}

	public void addMergedSource(Source source) {
		dataTree.unsaved.state.addMergedSource(source);
	}

	public void addDataSavedHandler(DataSavedHandler handler) {
		dataTree.unsaved.state.addDataSavedHandler(handler);
	}

	// *** Other information ***

	public Hash part() {
		Item item = dataTree.get(this);
		if (item == null) return null;
		if (item.part == null) return null;
		if (item.part.hashAndKey == null) return null;
		return item.part.hashAndKey.hash;
	}
}
