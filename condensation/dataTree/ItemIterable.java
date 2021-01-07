package condensation.dataTree;

import java.util.Iterator;

public final class ItemIterable implements Iterable<Item> {
	public final Iterator<Item> iterator;

	public ItemIterable(Iterator<Item> iterator) {
		this.iterator = iterator;
	}

	@Override
	public Iterator<Item> iterator() {
		return iterator;
	}
}
