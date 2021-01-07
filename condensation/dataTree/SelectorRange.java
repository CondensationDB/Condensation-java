package condensation.dataTree;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import condensation.ImmutableStack;
import condensation.serialization.Bytes;

// A SelectorRange describes a range of selectors using constraints on the selector labels, and provides efficient functions to check whether a given selector is within the range (range.contains(selector)) and to iterate over all existing
// selectors in the range (list(), iterator()).
//
// E.g., the range "/messages/*/data" describes the data selectors of all messages. Such a range is constructed as follows:
//      SelectorRange range = dataTree.root.child(BC.messages).any().child(BC.data);
// To check whether a selector is part of the range, use:
//      if (range.contains(selector)) { ... }
// To list all selectors in the range, use:
//      ArrayList<Selector> selectors = range.list();
// or simply iterate over the range:
//      for (Selector selector: range) { ... }
//
// Note that a range only includes the leaf selectors, i.e., the above range matches
//      /messages/A/data
//      /messages/B/data
// but not "/messages/A", "/messages", or "/messages/A/data/extended". Hence, the ranges "/messages/*" and "/messages/*/*" are different and non-overlapping.
//
// To iterate over a list of children of a selector (e.g. "/messages/*"), it is a bit more efficient to use
//      for (Selector child: messages.children()) { ... }
// instead of
//      for (Selector child: messages.any()) { ... }
// although the two a functionally equivalent.
//
// You may implement your own constraint:
//      class Length16 extends SelectorRange.Constraint {
//          @Override
//          boolean contains(Selector selector) {
//              return selector.label.byteLength == 16;
//          }
//      }
// and use it as follows:
//      Selector messageData = dataTree.root.child(BC.messages).some(new Length16()).child(BC.data);
// If your constraint matches only one (or a few) predictable selector, you may also want to override the traverse(...) method. Check out the implementation of SelectorRange.Constant for more information.
public class SelectorRange implements Iterable<Selector> {
	public final DataTree dataTree;
	public final SelectorRange parent;
	public final Selector parentSelector;
	public final Constraint constraint;
	public final int depth;

	SelectorRange(Selector parent, Constraint constraint) {
		this.dataTree = parent.dataTree;
		this.parent = null;
		this.parentSelector = parent;
		this.constraint = constraint;
		this.depth = parent.depth + 1;
	}

	SelectorRange(SelectorRange parent, Constraint constraint) {
		this.dataTree = parent.dataTree;
		this.parent = parent;
		this.parentSelector = null;
		this.constraint = constraint;
		this.depth = parent.depth + 1;
	}

	@Override
	public String toString() {
		return (parent == null ? parentSelector.toString() : parent.toString()) + "/" + constraint.toString();
	}

	public SelectorRange any() {
		return new SelectorRange(this, any);
	}

	public SelectorRange some(Constraint constraint) {
		return new SelectorRange(this, constraint);
	}

	public SelectorRange child(Bytes bytes) {
		return new SelectorRange(this, new Constant(bytes));
	}

	// Checks if the provided selector is within this range, i.e. matches all constraints.
	public boolean contains(@NonNull Selector selector) {
		if (selector.depth != depth) return false;

		SelectorRange matcher = this;
		while (true) {
			if (!matcher.constraint.contains(selector)) return false;

			selector = selector.parent;
			if (matcher.parent == null)
				return matcher.parentSelector.equals(selector);

			matcher = matcher.parent;
		}
	}

	// Checks if the provided selector is within this range, i.e. matches all constraints.
	public Selector[] match(@NonNull Selector selector) {
		if (selector.depth != depth) return null;

		SelectorRange matcher = this;
		Selector[] match = new Selector[selector.depth];
		while (true) {
			if (!matcher.constraint.contains(selector)) return null;
			match[selector.depth - 1] = selector;

			selector = selector.parent;
			if (matcher.parent == null)
				return matcher.parentSelector.equals(selector) ? match : null;

			matcher = matcher.parent;
		}
	}

	// Returns the ancestor which is part of the range, if any.
	public Selector ancestorOf(@NonNull Selector selector) {
		while (selector.depth > depth) selector = selector.parent;
		return contains(selector) ? selector : null;
	}

	// Returns a list of all existing (but not necessarily set) selectors in the range.
	public ArrayList<Selector> list() {
		Enlist enlist = new Enlist();
		SelectorRange root = this;
		ImmutableStack<Constraint> constraints = new ImmutableStack<Constraint>(enlist);
		while (true) {
			constraints = constraints.with(root.constraint);
			if (root.parent == null) break;
			root = root.parent;
		}

		Item item = dataTree.get(root.parentSelector);
		if (item == null) return enlist.list;
		constraints.head.traverse(constraints.tail, item);
		return enlist.list;
	}

	@Override
	public java.util.Iterator<Selector> iterator() {
		return list().iterator();
	}

	public Selector base() {
		SelectorRange current = this;
		while (current.parent != null) current = current.parent;
		return current.parentSelector;
	}

	abstract public static class Constraint {
		abstract boolean contains(Selector selector);

		void traverse(ImmutableStack<Constraint> constraints, Item item) {
			for (Item child = item.firstChild; child != null; child = child.nextSibling)
				if (contains(child.selector))
					constraints.head.traverse(constraints.tail, child);
		}
	}

	// Constraint accepting any label.
	static Constraint any = new Constraint() {
		@Override
		boolean contains(Selector selector) {
			return true;
		}

		@Override
		public String toString() {
			return "*";
		}
	};

	// Single-selector constraint with a constant label.
	static class Constant extends Constraint {
		final Bytes label;

		Constant(Bytes label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label.toString();
		}

		@Override
		boolean contains(Selector selector) {
			return selector.label.equals(label);
		}

		@Override
		void traverse(ImmutableStack<Constraint> constraints, Item item) {
			Item childItem = item.dataTree.get(item.selector.child(label));
			if (childItem == null) return;
			constraints.head.traverse(constraints.tail, childItem);
		}
	}

	// Pseudo-constraint to enlist the result.
	static class Enlist extends Constraint {
		ArrayList<Selector> list = new ArrayList<>();

		@Override
		boolean contains(Selector selector) {
			return false;
		}

		@Override
		void traverse(ImmutableStack<Constraint> constraints, Item item) {
			list.add(item.selector);
		}
	}
}
