package condensation.dataTree;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class ChildIterator implements Iterator<Item> {
	private Item next;

	ChildIterator(Item item) {
		next = item == null ? null : item.firstChild;
	}

	@Override
	public boolean hasNext() {
		return next != null;
	}

	@Override
	public Item next() {
		if (next == null) throw new NoSuchElementException();
		Item result = next;
		next = next.nextSibling;
		return result;
	}

	@Override
	public void remove() {
	}
}
