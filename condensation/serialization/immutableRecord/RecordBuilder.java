package condensation.serialization.immutableRecord;

import androidx.annotation.NonNull;

import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;

public class RecordBuilder {
	public Bytes bytes = Bytes.empty;
	public Hash hash = null;
	public RecordBuilder lastChild = null;
	public RecordBuilder previousSibling = null;

	public RecordBuilder() {
	}

	public RecordBuilder(@NonNull Bytes bytes) {
		this.bytes = bytes;
	}

	public RecordBuilder(@NonNull Bytes bytes, Hash hash) {
		this.bytes = bytes;
		this.hash = hash;
	}

	// *** Adding records

	public RecordBuilder add(Bytes bytes, Hash hash) {
		RecordBuilder record = new RecordBuilder(bytes, hash);
		record.previousSibling = lastChild;
		lastChild = record;
		return record;
	}

	public RecordBuilder add(Bytes bytes) {
		return add(bytes, null);
	}

	public RecordBuilder add(byte[] bytes) {
		return add(new Bytes(bytes), null);
	}

	public RecordBuilder add(String value) {
		return add(Bytes.fromText(value), null);
	}

	public RecordBuilder add(boolean value) {
		return add(Bytes.fromBoolean(value));
	}

	public RecordBuilder add(long value) {
		return add(Bytes.fromInteger(value));
	}

	public RecordBuilder addUnsigned(long value) {
		return add(Bytes.fromUnsigned(value));
	}

	public RecordBuilder add(String value, Hash hash) {
		return add(Bytes.fromText(value), hash);
	}

	public RecordBuilder add(boolean value, Hash hash) {
		return add(Bytes.fromBoolean(value), hash);
	}

	public RecordBuilder add(long value, Hash hash) {
		return add(Bytes.fromInteger(value), hash);
	}

	public RecordBuilder addUnsigned(long value, Hash hash) {
		return add(Bytes.fromUnsigned(value), hash);
	}

	public RecordBuilder add(Hash hash) {
		return add(Bytes.empty, hash);
	}

	public RecordBuilder add(HashAndKey hashAndKey) {
		return add(hashAndKey.key, hashAndKey.hash);
	}

	public void add(RecordBuilder record) {
		record.previousSibling = lastChild;
		lastChild = record;
	}

	public void add(ImmutableRecord record) {
		RecordBuilder added = add(record.bytes, record.hash);
		for (ImmutableRecord child : record.children)
			added.add(child);
	}

	public void add(Iterable<ImmutableRecord> records) {
		for (ImmutableRecord item : records) add(item);
	}

	public boolean add(CondensationObject object) {
		if (object.data.byteLength == 0) return true;
		return new RecordReader(object, object.data).readChildren(this);
	}

	// This returns an object with a fresh byte buffer. To encrypt the object, you may call object.cryptInplace() before using the object.
	public CondensationObject toObject() {
		return new RecordWriter(this).toObject();
	}
}
