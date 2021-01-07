package condensation;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class ImmutableStack<T> implements Iterable<T> {
	public final int length;
	public final T head;                    // null if length == 0, not null otherwise
	public final ImmutableStack<T> tail;    // null if length == 0, not null otherwise

	public ImmutableStack() {
		this.length = 0;
		this.head = null;
		this.tail = null;
	}

	public ImmutableStack(T element) {
		this.length = 1;
		this.head = element;
		this.tail = new ImmutableStack<T>();
	}

	public ImmutableStack(ImmutableStack<T> tail, T element) {
		this.length = tail.length + 1;
		this.head = element;
		this.tail = tail;
	}

	// O(1)
	public ImmutableStack<T> with(T element) {
		return new ImmutableStack<T>(this, element);
	}

	// O(N)
	public ImmutableStack<T> without(T element) {
		ImmutableStack<T> stack = new ImmutableStack<T>();

		for (T existingElement : this) {
			if (existingElement.equals(element)) continue;
			stack = new ImmutableStack<T>(stack, existingElement);
		}

		return stack;
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof ImmutableStack && equals((ImmutableStack<T>) that);
	}

	public boolean equals(ImmutableStack<T> that) {
		if (length != that.length) return false;
		for (ImmutableStack<T> element = this; element.length > 0; element = element.tail, that = that.tail)
			if (!element.head.equals(that.head)) return false;
		return true;
	}

	@Override
	public int hashCode() {
		if (length == 0) return 0xf0c5;
		int hashCode = tail.hashCode();
		return (hashCode << 12) ^ ((hashCode & 0xfff00000) >> 20) ^ head.hashCode();
	}

	@Override
	public Iterator<T> iterator() {
		return new LifoIterator<T>(this);
	}

	static class LifoIterator<T> implements Iterator<T> {
		ImmutableStack<T> remaining;

		public LifoIterator(ImmutableStack<T> stack) {
			this.remaining = stack;
		}

		public boolean hasNext() {
			return remaining.length > 0;
		}

		public T next() {
			if (remaining.length == 0)
				throw new NoSuchElementException();
			T item = remaining.head;
			remaining = remaining.tail;
			return item;
		}

		@Override
		public void remove() {
		}
	}
}
