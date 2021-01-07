package condensation.serialization.immutableRecord;

import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

public class RecordWriter {
	int hashesCount = 0;
	int dataLength = 0;
	final int headerLength;
	final Bytes bytes;
	int nextHashIndex = 0;
	int pos;

	public RecordWriter(RecordBuilder record) {
		// Prepare
		hashesCount = 0;
		dataLength = 0;
		prepareChildren(record);
		dataLength += hashesCount * 4;
		headerLength = 4 + hashesCount * 32;
		bytes = new Bytes(headerLength + dataLength);

		// Write the object
		bytes.setUnsigned32(0, hashesCount);
		pos = headerLength + dataLength;
		writeChildren(record);
	}

	void prepareChildren(RecordBuilder record) {
		for (RecordBuilder child = record.lastChild; child != null; child = child.previousSibling) {
			int byteLength = child.bytes.byteLength;
			dataLength += byteLength < 30 ? 1 : byteLength < 286 ? 2 : 9;
			dataLength += byteLength;
			if (child.hash != null) hashesCount += 1;
			prepareChildren(child);
		}
	}

	void writeChildren(RecordBuilder record) {
		RecordBuilder child = record.lastChild;
		if (child == null) return;
		writeNode(child, false);
		child = child.previousSibling;
		while (child != null) {
			writeNode(child, true);
			child = child.previousSibling;
		}
	}

	// https://condensation.io/actors/records/
	void writeNode(RecordBuilder record, boolean hasMoreSiblings) {
		// Children
		writeChildren(record);

		// Flags
		int byteLength = record.bytes.byteLength;
		int flags = byteLength < 30 ? byteLength : byteLength < 286 ? 30 : 31;
		if (record.hash != null) flags |= 0x20;
		if (record.lastChild != null) flags |= 0x40;
		if (hasMoreSiblings) flags |= 0x80;
		this.writeUnsigned8((short) flags);

		// Data
		if ((flags & 0x1f) == 30) this.writeUnsigned8((short) (byteLength - 30));
		if ((flags & 0x1f) == 31) this.writeUnsigned64(byteLength);
		this.writeBytes(record.bytes);
		if ((flags & 0x20) != 0) this.writeUnsigned32(this.addHash(record.hash));
	}

	void writeUnsigned8(short value) {
		pos -= 1;
		bytes.setUnsigned8(pos, value);
	}

	void writeUnsigned32(long value) {
		pos -= 4;
		bytes.setUnsigned32(pos, value);
	}

	void writeUnsigned64(long value) {
		pos -= 8;
		bytes.setInteger64(pos, value);
	}

	void writeBytes(Bytes newBytes) {
		pos -= newBytes.byteLength;
		bytes.set(pos, newBytes);
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
