package condensation.serialization;

public class RecordWriter {
	int hashesCount = 0;
	int dataLength = 0;
	final int headerLength;
	final Bytes bytes;
	int nextHashIndex = 0;
	int pos;

	public RecordWriter(Record record) {
		// Prepare
		hashesCount = 0;
		dataLength = 0;
		prepareChildren(record);
		dataLength += hashesCount * 4;
		headerLength = 4 + hashesCount * 32;
		bytes = new Bytes(headerLength + dataLength);

		// Write the object
		bytes.setUnsigned32(0, hashesCount);
		pos = headerLength;
		writeChildren(record);
	}

	void prepareChildren(Record record) {
		int count = record.children.size();
		for (int i = 0; i < count; i++) {
			Record child = record.children.get(i);
			int byteLength = child.bytes.byteLength;
			dataLength += byteLength < 30 ? 1 : byteLength < 286 ? 2 : 9;
			dataLength += byteLength;
			if (child.hash != null) hashesCount += 1;
			prepareChildren(child);
		}
	}

	void writeChildren(Record record) {
		int count = record.children.size();
		for (int i = 0; i < count - 1; i++) writeNode(record.children.get(i), true);
		if (count > 0) writeNode(record.children.get(count - 1), false);
	}

	// https://condensation.io/actors/records/
	void writeNode(Record record, boolean hasMoreSiblings) {
		// Flags
		int byteLength = record.bytes.byteLength;
		int flags = byteLength < 30 ? byteLength : byteLength < 286 ? 30 : 31;
		if (record.hash != null) flags |= 0x20;
		if (record.children.size() > 0) flags |= 0x40;
		if (hasMoreSiblings) flags |= 0x80;
		this.writeUnsigned8((short) flags);

		// Data
		if ((flags & 0x1f) == 30) this.writeUnsigned8((short) (byteLength - 30));
		if ((flags & 0x1f) == 31) this.writeUnsigned64(byteLength);
		this.writeBytes(record.bytes);
		if ((flags & 0x20) != 0) this.writeUnsigned32(this.addHash(record.hash));

		// Children
		writeChildren(record);
	}

	void writeUnsigned8(short value) {
		bytes.setUnsigned8(pos, value);
		pos += 1;
	}

	void writeUnsigned32(long value) {
		bytes.setUnsigned32(pos, value);
		pos += 4;
	}

	void writeUnsigned64(long value) {
		bytes.setInteger64(pos, value);
		pos += 8;
	}

	void writeBytes(Bytes newBytes) {
		bytes.set(pos, newBytes);
		pos += newBytes.byteLength;
	}

	int addHash(Hash hash) {
		int index = nextHashIndex;
		bytes.set(4 + index * 32, hash.bytes);
		nextHashIndex += 1;
		return index;
	}

	public CondensationObject toObject() {
		return new CondensationObject(hashesCount, bytes.slice(0, headerLength), bytes.slice(headerLength, dataLength));
	}
}
