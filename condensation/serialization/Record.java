package condensation.serialization;

import androidx.annotation.NonNull;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashSet;

public class Record {
	// *** Static ***

	public static Record from(CondensationObject object) {
		if (object == null) return null;
		Record root = new Record();
		return root.add(object) ? root : null;
	}

	// *** Object ***

	public Bytes bytes = Bytes.empty;
	public Hash hash = null;
	public final ArrayList<Record> children = new ArrayList<>();        // this should be final, but the serializable interface prevents it, as it does not initialize it  (TODO: remove the serializable interface)

	public Record() {
	}

	public Record(@NonNull Bytes bytes) {
		this.bytes = bytes;
	}

	public Record(@NonNull Bytes bytes, Hash hash) {
		//if (bytes == null) Condensation.logError("Record BYTES = null");
		this.bytes = bytes;
		this.hash = hash;
	}

	public Record deepClone() {
		Record record = new Record(bytes, hash);
		for (Record child : children)
			record.add(child.deepClone());
		return record;
	}

	// *** Setting data

	public void set(@NonNull Bytes bytes, Hash hash) {
		//if (bytes == null) Condensation.logError("Record.set BYTES = null");
		this.bytes = bytes;
		this.hash = hash;
	}

	// *** Adding records

	public Record add(Bytes bytes, Hash hash) {
		Record record = new Record(bytes, hash);
		children.add(record);
		return record;
	}

	public Record add(Bytes bytes) {
		return add(bytes, null);
	}

	public Record add(byte[] bytes) {
		return add(new Bytes(bytes), null);
	}

	public Record add(String value) {
		return add(Bytes.fromText(value), null);
	}

	public Record add(boolean value) {
		return add(Bytes.fromBoolean(value));
	}

	public Record add(long value) {
		return add(Bytes.fromInteger(value));
	}

	public Record addUnsigned(long value) {
		return add(Bytes.fromUnsigned(value));
	}

	public Record add(String value, Hash hash) {
		return add(Bytes.fromText(value), hash);
	}

	public Record add(boolean value, Hash hash) {
		return add(Bytes.fromBoolean(value), hash);
	}

	public Record add(long value, Hash hash) {
		return add(Bytes.fromInteger(value), hash);
	}

	public Record addUnsigned(long value, Hash hash) {
		return add(Bytes.fromUnsigned(value), hash);
	}

	public Record add(Hash hash) {
		return add(Bytes.empty, hash);
	}

	public Record add(HashAndKey hashAndKey) {
		return add(hashAndKey.key, hashAndKey.hash);
	}

	public void add(Record record) {
		children.add(record);
	}

	public void add(Iterable<Record> records) {
		for (Record item : records)
			children.add(item);
	}

	public boolean add(CondensationObject object) {
		if (object.data.byteLength == 0) return true;
		return new RecordReader(object).readChildren(this);
	}

	// *** Querying

	public boolean contains(Bytes bytes) {
		for (Record record : children)
			if (record.bytes.equals(bytes)) return true;
		return false;
	}

	public boolean contains(String text) {
		return contains(Bytes.fromText(text));
	}

	public Record child(Bytes bytes) {
		for (Record record : children)
			if (record.bytes.equals(bytes)) return record;
		return new Record();
	}

	public Record child(String text) {
		return child(Bytes.fromText(text));
	}

	public Record child(int index) {
		return children.size() > index ? children.get(index) : new Record();
	}

	public Record firstChild() {
		return children.size() > 0 ? children.get(0) : new Record();
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

	public HashAndKey hashAndKeyValue() {
		return firstChild().asHashAndKey();
	}

	// *** Dependent hashes

	public HashSet<Hash> dependentHashes() {
		HashSet<Hash> hashes = new HashSet<>();
		traverseHashes(hashes);
		return hashes;
	}

	private void traverseHashes(HashSet<Hash> hashes) {
		if (hash != null) hashes.add(hash);
		for (Record child : children)
			child.traverseHashes(hashes);
	}

	// *** Size

	public int countEntries() {
		int count = 1;
		for (Record child : children)
			count += child.countEntries();
		return count;
	}

	public long calculateSize() {
		return 4 + calculateSizeContribution();
	}

	private long calculateSizeContribution() {
		long size = bytes.byteLength < 30 ? 1 : bytes.byteLength < 286 ? 2 : 9;
		size += bytes.byteLength;
		if (hash != null) size += 32 + 4;
		for (Record child : children)
			size += child.calculateSizeContribution();
		return size;
	}

	// *** Serialization

	// This returns an object with a fresh byte buffer. To encrypt the object, you may call object.cryptInplace() before using the object.
	public CondensationObject toObject() {
		return new RecordWriter(this).toObject();
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof Record && (this == that || equals((Record) that));
	}

	public boolean equals(Record that) {
		if (that == null) return false;
		if (that == this) return true;

		if (!bytes.equals(that.bytes)) return false;
		if (!Hash.equals(hash, that.hash)) return false;
		if (children.size() != that.children.size()) return false;

		int size = children.size();
		for (int i = 0; i < size; i++)
			if (!children.get(i).equals(that.children.get(i))) return false;

		return true;
	}

	@Override
	public int hashCode() {
		int hashCode = bytes.hashCode();
		if (hash != null) hashCode ^= hash.hashCode();
		for (Record child : children) hashCode ^= child.hashCode();
		return hashCode;
	}

	public String toString() {
		StringBuilder text = new StringBuilder();
		addTo(text, "");
		return text.toString();
	}

	public void addTo(StringBuilder text, String indent) {
		String bytesText;
		try {
			CharBuffer decoded = Bytes.utf8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(bytes.createByteBuffer());
			bytesText = decoded.toString();
			if (bytesText.length() > 80) bytesText = bytesText.substring(0, 64) + "...";
		} catch (CharacterCodingException e) {
			bytesText = bytes.byteLength > 40 ? bytes.slice(0, 32).asHex() + "..." : bytes.asHex();
		}

		String hashText = hash == null ? "" : " # " + hash.shortHex();
		text.append(indent).append(bytesText).append(hashText).append("\n");

		String childIndent = indent + "  ";
		for (Record child : children) child.addTo(text, childIndent);
	}
}
