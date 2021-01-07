package condensation.dataTree;

import java.util.Iterator;

class Notifier implements Runnable {
	public final DataTree dataTree;

	// State
	int count = 0;

	Notifier(DataTree dataTree) {
		this.dataTree = dataTree;
	}

	@Override
	public void run() {
		new Run();
	}

	class Run {
		final Item[] items;
		final int[] subTreeEnd;
		final byte[] notifyFlags;
		int w;

		Run() {
			// Create a snapshot of all changes, and reset the changes at the same time
			items = new Item[count];
			subTreeEnd = new int[count];
			notifyFlags = new byte[count];
			w = 0;
			addItem(dataTree.rootItem());
			count = 0;

			// Notify
			//Condensation.startPerformance();
			while (w > 0) {
				w -= 1;
				Item item = items[w];

				if ((notifyFlags[w] & Item.notifyValueParent) != 0)
					for (BranchListener listener : item.branchListeners)
						listener.onBranchChanged(new ChangeSet());

				if ((notifyFlags[w] & Item.notifyValueItem) != 0)
					for (ValueListener listener : item.valueListeners)
						listener.onValueChanged();

				if ((notifyFlags[w] & Item.notifyPrune) != 0)
					item.pruneIfPossible();
			}

			//Condensation.logPerformance("checked " + items.length + " potentially pruned " + countPruned + " remaining " + dataTree.itemsBySelector.size());
		}

		private void addItem(Item item) {
			int index = w;
			items[index] = item;
			notifyFlags[index] = item.notifyFlags;
			item.notifyFlags = 0;
			w += 1;

			Item current = item.notifyChild;
			item.notifyChild = null;
			while (current != null) {
				addItem(current);
				Item next = current.notifySibling;
				current.notifySibling = null;
				current = next;
			}

			subTreeEnd[index] = w;
		}

		class ChangeSet implements Iterable<Selector> {
			final int start;
			final int end;

			ChangeSet() {
				this.start = w;
				this.end = subTreeEnd[w];
			}

			@Override
			public Iterator<Selector> iterator() {
				return new ChangeIterator(start, end);
			}
		}

		class ChangeIterator implements Iterator<Selector> {
			int current;
			final int end;

			ChangeIterator(int start, int end) {
				this.current = start - 1;
				this.end = end;
				moveToNext();
			}

			void moveToNext() {
				while (true) {
					current += 1;
					if (current >= end) return;
					if ((notifyFlags[current] & Item.notifyValueItem) != 0) return;
				}
			}

			@Override
			public boolean hasNext() {
				return current < end;
			}

			@Override
			public Selector next() {
				if (current >= end) return null;
				Selector result = items[current].selector;
				moveToNext();
				return result;
			}

			@Override
			public void remove() {
			}
		}
	}
}

