package condensation;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.NoSuchElementException;

public class ImmutableList<T> implements Collection<T> {
	// *** Static ***

	public static <T> ImmutableList<T> fifo(ImmutableStack<T> stack) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[stack.length];
		for (ImmutableStack<T> remaining = stack; remaining.length > 0; remaining = remaining.tail)
			array[remaining.length - 1] = remaining.head;
		return new ImmutableList<T>(array, 0, stack.length);
	}

	public static <T> ImmutableList<T> lifo(ImmutableStack<T> stack) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[stack.length];
		int i = 0;
		for (ImmutableStack<T> remaining = stack; remaining.length > 0; remaining = remaining.tail, i++)
			array[i] = remaining.head;
		return new ImmutableList<T>(array, 0, stack.length);
	}

	public static <T> ImmutableList<T> sorted(ImmutableStack<T> stack) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[stack.length];
		for (ImmutableStack<T> remaining = stack; remaining.length > 0; remaining = remaining.tail)
			array[remaining.length - 1] = remaining.head;
		Arrays.sort(array);
		return new ImmutableList<T>(array, 0, stack.length);
	}

	public static <T> ImmutableList<T> sorted(ImmutableStack<T> stack, Comparator<T> comparator) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[stack.length];
		for (ImmutableStack<T> remaining = stack; remaining.length > 0; remaining = remaining.tail)
			array[remaining.length - 1] = remaining.head;
		Arrays.sort(array, comparator);
		return new ImmutableList<T>(array, 0, stack.length);
	}

	public static <T> ImmutableList<T> sorted(Collection<T> collection) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[collection.size()];
		int i = 0;
		for (T element : collection)
			array[i++] = element;
		Arrays.sort(array);
		return new ImmutableList<T>(array, 0, i);
	}

	public static <T> ImmutableList<T> sorted(Collection<T> collection, Comparator<T> comparator) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[collection.size()];
		int i = 0;
		for (T element : collection)
			array[i++] = element;
		Arrays.sort(array, comparator);
		return new ImmutableList<T>(array, 0, i);
	}

	public static <T> ImmutableList<T> from(Collection<T> collection) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[collection.size()];
		int i = 0;
		for (T element : collection)
			array[i++] = element;

		return new ImmutableList<T>(array, 0, i);
	}

	public static <T> ImmutableList<T> create(T... items) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[items.length];
		for (int i = 0; i < items.length; i++)
			array[i] = items[i];
		return new ImmutableList<>(array, 0, array.length);
	}

	// *** Object ***
	public final T[] array;
	public final int offset;
	public final int length;

	public ImmutableList() {
		this.array = (T[]) new Object[0];
		this.offset = 0;
		this.length = 0;
	}

	public ImmutableList(T[] array, int offset, int length) {
		this.array = array;
		this.offset = offset;
		this.length = length;
	}

	public ImmutableList<T> slice(int offset, int length) {
		if (offset < 0) offset = 0;
		if (offset + length > this.length) length = this.length - offset;
		if (length < 1) return new ImmutableList<T>();
		return new ImmutableList<T>(array, this.offset + offset, length);
	}

	public T get(int index) {
		return array[offset + index];
	}

	@Override
	public boolean add(T object) {
		return false;
	}

	@Override
	public boolean addAll(@NonNull Collection<? extends T> collection) {
		return false;
	}

	@Override
	public void clear() {
	}

	@Override
	public boolean contains(Object object) {
		return false;
	}

	@Override
	public boolean containsAll(@NonNull Collection<?> collection) {
		return false;
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof ImmutableList && equals((ImmutableList<T>) that);
	}

	public boolean equals(ImmutableList<T> that) {
		if (length != that.length) return false;
		for (int i = 0; i < length; i++)
			if (!array[offset + i].equals(that.array[that.offset + i])) return false;
		return true;
	}

	@Override
	public int hashCode() {
		int hashCode = length;
		for (int i = 0; i < length; i++)
			hashCode = (hashCode << 12) ^ ((hashCode & 0xfff00000) >> 20) ^ array[offset + i].hashCode();
		return hashCode;
	}

	@Override
	public boolean isEmpty() {
		return length == 0;
	}

	@Override
	public java.util.Iterator<T> iterator() {
		return new Iterator();
	}

	@Override
	public boolean remove(Object object) {
		return false;
	}

	@Override
	public boolean removeAll(@NonNull Collection<?> collection) {
		return false;
	}

	@Override
	public boolean retainAll(@NonNull Collection<?> collection) {
		return false;
	}

	@Override
	public int size() {
		return length;
	}

	@NonNull
	public ImmutableList<T> prependedWith(T item) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[length + 1];
		array[0] = item;
		System.arraycopy(this.array, this.offset, array, 1, this.length);
		return new ImmutableList<T>(array, 0, array.length);
	}

	@NonNull
	public ImmutableList<T> with(T item) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[length + 1];
		System.arraycopy(this.array, this.offset, array, 0, this.length);
		array[length] = item;
		return new ImmutableList<T>(array, 0, array.length);
	}

	@NonNull
	public ImmutableList<T> with(ImmutableList<T> list) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[length + list.length];
		System.arraycopy(this.array, this.offset, array, 0, this.length);
		System.arraycopy(list.array, list.offset, array, this.length, list.length);
		return new ImmutableList<T>(array, 0, array.length);
	}

	@NonNull
	public ImmutableList<T> sorted() {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[length];
		System.arraycopy(this.array, this.offset, array, 0, this.length);
		Arrays.sort(array);
		return new ImmutableList<>(array, 0, array.length);
	}

	@NonNull
	public ImmutableList<T> sorted(Comparator<T> comparator) {
		@SuppressWarnings("unchecked") T[] array = (T[]) new Object[length];
		System.arraycopy(this.array, this.offset, array, 0, this.length);
		Arrays.sort(array, comparator);
		return new ImmutableList<>(array, 0, array.length);
	}

	@NonNull
	@Override
	public Object[] toArray() {
		Object[] result = new Object[length];
		System.arraycopy(array, offset, result, 0, length);
		return result;
	}

	@NonNull
	@Override
	@SuppressWarnings("unchecked")
	public <T1> T1[] toArray(@NonNull T1[] result) {
		if (result.length < length)
			result = (T1[]) new Object[length];
		System.arraycopy(array, offset, result, 0, length);
		return result;
	}

	class Iterator implements java.util.Iterator<T> {
		int index = 0;

		public boolean hasNext() {
			return index < length;
		}

		public T next() {
			if (index >= length)
				throw new NoSuchElementException();
			T item = array[offset + index];
			index += 1;
			return item;
		}

		@Override
		public void remove() {
		}
	}
}
