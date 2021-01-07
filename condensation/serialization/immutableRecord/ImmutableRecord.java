package condensation.serialization.immutableRecord;

import androidx.annotation.NonNull;

import java.math.BigInteger;

import condensation.ImmutableList;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;

public class ImmutableRecord {
	public static final ImmutableRecord empty = new ImmutableRecord(Bytes.empty, null, new ImmutableList<ImmutableRecord>());
	static final ImmutableList<ImmutableRecord> noChildren = new ImmutableList<>();

	// *** Static ***

	public static Record from(CondensationObject object) {
		if (object == null) return null;
		Record root = new Record();
		return root.add(object) ? root : null;
	}

	public static ImmutableRecord from(RecordBuilder record) {
		int countChildren = 0;
		for (RecordBuilder child = record.lastChild; child != null; child = child.previousSibling)
			countChildren += 1;

		if (countChildren > 0) {
			ImmutableRecord[] children = new ImmutableRecord[countChildren];
			int w = countChildren;
			for (RecordBuilder child = record.lastChild; child != null; child = child.previousSibling) {
				w -= 1;
				children[w] = from(child);
			}
			return new ImmutableRecord(record.bytes, record.hash, new ImmutableList<>(children, 0, children.length));
		} else {
			return new ImmutableRecord(record.bytes, record.hash, noChildren);
		}
	}

	// *** Object ***

	public final Bytes bytes;
	public final Hash hash;
	public final ImmutableList<ImmutableRecord> children;

	public ImmutableRecord(@NonNull Bytes bytes, Hash hash, ImmutableList<ImmutableRecord> children) {
		this.bytes = bytes;
		this.hash = hash;
		this.children = children;
	}

	// *** Querying

	public boolean contains(Bytes bytes) {
		for (ImmutableRecord record : children)
			if (record.bytes.equals(bytes)) return true;
		return false;
	}

	public ImmutableRecord child(Bytes bytes) {
		for (ImmutableRecord record : children)
			if (record.bytes.equals(bytes)) return record;
		return empty;
	}

	public ImmutableRecord child(int index) {
		return children.length > index ? children.get(index) : empty;
	}

	public ImmutableRecord firstChild() {
		return children.length > 0 ? children.get(0) : empty;
	}

	// Retrieving values

	public String asText() {
		return bytes.asText();
	}

	public boolean asBoolean() {
		return bytes.asBoolean();
	}

	public long asInteger() {
		return bytes.asInteger();
	}

	public long asUnsigned() {
		return bytes.asUnsigned();
	}

	public BigInteger asBigInteger() {
		return new BigInteger(1, bytes.toByteArray());
	}

	public HashAndKey asHashAndKey() {
		return bytes.byteLength == 32 && hash != null ? new HashAndKey(hash, bytes) : null;
	}

	public Bytes bytesValue() {
		return firstChild().bytes;
	}

	public Hash hashValue() {
		return firstChild().hash;
	}

	public String textValue() {
		return firstChild().asText();
	}

	public boolean booleanValue() {
		return firstChild().asBoolean();
	}

	public long integerValue() {
		return firstChild().asInteger();
	}

	public long unsignedValue() {
		return firstChild().asUnsigned();
	}

	public BigInteger bigIntegerValue() {
		return firstChild().asBigInteger();
	}

	public HashAndKey referenceValue() {
		return firstChild().asHashAndKey();
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof ImmutableRecord && (this == that || equals((ImmutableRecord) that));
	}

	public boolean equals(ImmutableRecord that) {
		if (that == null) return false;
		if (that == this) return true;

		if (!bytes.equals(that.bytes)) return false;
		if (!Hash.equals(hash, that.hash)) return false;
		if (children.length != that.children.length) return false;

		int size = children.length;
		for (int i = 0; i < size; i++)
			if (!children.get(i).equals(that.children.get(i))) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int hashCode = bytes.hashCode();
		if (hash != null) hashCode ^= hash.hashCode();
		for (ImmutableRecord child : children) hashCode ^= child.hashCode();
		return hashCode;
	}
}
