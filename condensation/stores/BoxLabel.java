package condensation.stores;

import androidx.annotation.NonNull;

import condensation.serialization.Bytes;

public final class BoxLabel implements Comparable<BoxLabel> {
	// *** Static ***
	public static final BoxLabel MESSAGES = new BoxLabel("messages");
	public static final BoxLabel PRIVATE = new BoxLabel("private");
	public static final BoxLabel PUBLIC = new BoxLabel("public");
	public static final BoxLabel[] all = new BoxLabel[3];

	static {
		all[0] = MESSAGES;
		all[1] = PRIVATE;
		all[2] = PUBLIC;
	}

	public static BoxLabel fromBytes(Bytes bytes) {
		if (bytes.equals(MESSAGES.asBytes)) return MESSAGES;
		if (bytes.equals(PRIVATE.asBytes)) return PRIVATE;
		if (bytes.equals(PUBLIC.asBytes)) return PUBLIC;
		return null;
	}

	public static BoxLabel fromText(String text) {
		if (text.equals(MESSAGES.asText)) return MESSAGES;
		if (text.equals(PRIVATE.asText)) return PRIVATE;
		if (text.equals(PUBLIC.asText)) return PUBLIC;
		return null;
	}

	// *** Object ***
	public final String asText;
	public final Bytes asBytes;

	public BoxLabel(String label) {
		asText = label;
		asBytes = Bytes.fromText(label);
	}

	@Override
	public String toString() {
		return asText;
	}

	public int compareTo(@NonNull BoxLabel that) {
		return asBytes.compareTo(that.asBytes);
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof BoxLabel && (this == that || equals((BoxLabel) that));
	}

	public boolean equals(BoxLabel that) {
		if (that == null) return false;
		if (that == this) return true;
		return asBytes.equals(that.asBytes);
	}

	@Override
	public int hashCode() {
		return asBytes.hashCode();
	}

	public static boolean equals(BoxLabel a, BoxLabel b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		return a.equals(b);
	}
}
