package condensation.serialization;

public class RecordReader {
	public final CondensationObject object;
	final Bytes data;
	int pos = 0;
	public boolean hasError = false;

	public RecordReader(CondensationObject object) {
		this.object = object;
		this.data = object.data;
	}

	public boolean readChildren(Record record) {
		while (true) {
			// Flags
			int flags = readUnsigned8();

			// Data
			int length = flags & 0x1f;
			long byteLength = length == 30 ? 30 + this.readUnsigned8() : length == 31 ? this.readUnsigned64() : length;
			if (byteLength > 0x7fffffff) return false;
			Bytes bytes = this.readBytes((int) byteLength);
			Hash hash = (flags & 0x20) != 0 ? object.hashAtIndex((int) this.readUnsigned32()) : null;
			if (this.hasError) return false;

			// Children
			Record child = record.add(bytes, hash);
			if ((flags & 0x40) != 0 && !readChildren(child)) return false;
			if ((flags & 0x80) == 0) return true;
		}
	}

	public boolean use(int length) {
		pos += length;
		hasError |= pos > data.byteLength;
		return hasError;
	}

	int readUnsigned8() {
		int start = pos;
		if (use(1)) return 0;
		return data.getUnsigned8(start);
	}

	long readUnsigned32() {
		int start = pos;
		if (use(4)) return 0;
		return data.getUnsigned32(start);
	}

	// Java can only handle 63 unsigned bits, since it does not support an unsigned long data type. This is anyway enough for all practical purposes.
	// We simply shave off the sign bit, so that the returned value is positive as expected by the caller.
	long readUnsigned64() {
		int start = pos;
		if (use(8)) return 0;
		return data.getInteger64(start) & 0x7fffffffffffffffL;
	}

	Bytes readBytes(int length) {
		int start = pos;
		if (use(length)) return null;
		return data.slice(start, length);
	}

	public Bytes trailer() {
		return data.slice(pos);
	}
}
