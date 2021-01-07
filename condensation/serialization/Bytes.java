package condensation.serialization;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import condensation.Condensation;

public class Bytes implements Comparable<Bytes> {
	// *** Static ***
	public static final Bytes empty = new Bytes(new byte[0], 0, 0);
	public static final Bytes yes = new Bytes(new byte[]{121}, 0, 1);
	public static final Charset utf8 = Charset.forName("UTF-8");
	public static final char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
	public static final int[] hexValues = {255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 255, 255, 255, 255, 255, 255, 255, 10, 11, 12, 13, 14, 15, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 10, 11, 12, 13, 14, 15, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255};

	public static Bytes fromBoolean(boolean value) {
		return value ? yes : empty;
	}

	public static Bytes integer8(byte value) {
		Bytes bytes = new Bytes(1);
		bytes.setInteger8(0, value);
		return bytes;
	}

	public static Bytes integer16(short value) {
		Bytes bytes = new Bytes(2);
		bytes.setInteger16(0, value);
		return bytes;
	}

	public static Bytes integer32(int value) {
		Bytes bytes = new Bytes(4);
		bytes.setInteger32(0, value);
		return bytes;
	}

	public static Bytes integer64(long value) {
		Bytes bytes = new Bytes(8);
		bytes.setInteger64(0, value);
		return bytes;
	}

	public static Bytes unsigned8(short value) {
		Bytes bytes = new Bytes(1);
		bytes.setUnsigned8(0, value);
		return bytes;
	}

	public static Bytes unsigned16(int value) {
		Bytes bytes = new Bytes(2);
		bytes.setUnsigned16(0, value);
		return bytes;
	}

	public static Bytes unsigned32(long value) {
		Bytes bytes = new Bytes(4);
		bytes.setUnsigned32(0, value);
		return bytes;
	}

	public static Bytes fromInteger(long value) {
		if (value == 0) return Bytes.empty;
		Bytes bytes = new Bytes(8);
		int pos = 8;
		while (value < -128 || value >= 128) {
			pos -= 1;
			bytes.buffer[pos] = (byte) (value & 0xff);
			value >>= 8;
		}
		pos -= 1;
		bytes.buffer[pos] = (byte) (value & 0xff);
		return bytes.slice(pos);
	}

	public static Bytes fromUnsigned(long value) {
		if (value <= 0) return Bytes.empty;
		Bytes bytes = new Bytes(8);
		int pos = 8;
		while (value > 0) {
			pos -= 1;
			bytes.buffer[pos] = (byte) (value & 0xff);
			value >>>= 8;
		}
		return bytes.slice(pos);
	}

	public static Bytes fromText(String text) {
		return new Bytes(utf8.encode(text));
	}

	public static Bytes fromHex(String text) {
		if (text == null) return null;
		Bytes bytes = new Bytes(text.length() >> 1);
		for (int i = 0; i < bytes.byteLength; i++) {
			char c1 = text.charAt(i * 2);
			if (c1 > 127) return null;
			int h1 = hexValues[c1];
			if (h1 >= 16) return null;
			char c2 = text.charAt(i * 2 + 1);
			if (c2 > 127) return null;
			int h2 = hexValues[c2];
			if (h2 >= 16) return null;
			bytes.buffer[i] = (byte) ((h1 << 4) | h2);
		}
		return bytes;
	}

	public static Bytes from(File file) throws IOException {
		int size = (int) file.length();
		FileInputStream stream = new FileInputStream(file);
		byte[] bytes = Condensation.read(stream, size);
		if (bytes == null) return null;
		stream.close();
		return new Bytes(bytes);
	}

	public static Bytes concatenate(Bytes... chunks) {
		int length = 0;
		for (Bytes chunk : chunks) length += chunk.byteLength;

		int pos = 0;
		Bytes bytes = new Bytes(length);
		for (Bytes chunk : chunks) {
			bytes.set(pos, chunk);
			pos += chunk.byteLength;
		}

		return bytes;
	}

	public static Bytes concatenate(Iterable<Bytes> chunks) {
		int length = 0;
		for (Bytes chunk : chunks) length += chunk.byteLength;

		int pos = 0;
		Bytes bytes = new Bytes(length);
		for (Bytes chunk : chunks) {
			bytes.set(pos, chunk);
			pos += chunk.byteLength;
		}

		return bytes;
	}

	public static boolean equals(Bytes a, Bytes b) {
		if (a == b) return true;
		if (a == null || b == null) return false;
		return a.equals(b);
	}

	// *** Object ***
	public final byte[] buffer;
	public final int byteOffset;
	public final int byteLength;

	public Bytes(@NonNull byte[] buffer, int byteOffset, int byteLength) {
		this.buffer = buffer;
		this.byteOffset = byteOffset;
		this.byteLength = byteLength;
	}

	public Bytes(@NonNull byte[] buffer) {
		this.buffer = buffer;
		this.byteOffset = 0;
		this.byteLength = buffer.length;
	}

	public Bytes(@NonNull ByteBuffer byteBuffer) {
		this.buffer = byteBuffer.array();
		this.byteOffset = byteBuffer.arrayOffset();
		this.byteLength = byteBuffer.limit() - byteOffset;
	}

	public Bytes(int length) {
		this.buffer = new byte[length];
		this.byteOffset = 0;
		this.byteLength = length;
	}

	public ByteBuffer createByteBuffer() {
		return ByteBuffer.wrap(buffer, byteOffset, byteLength);
	}

	// The returned byte array may be a copy, or the actual array itself, and should therefore be treated read-only. If you need a modifiable copy, create your own byte array, and use copyToByteArray(...).
	public byte[] toByteArray() {
		if (byteOffset == 0 && byteLength == buffer.length) return buffer;
		byte[] bytes = new byte[byteLength];
		System.arraycopy(buffer, byteOffset, bytes, 0, byteLength);
		return bytes;
	}

	public void copyToByteArray(byte[] out, int outOffset) {
		System.arraycopy(buffer, byteOffset, out, outOffset, byteLength);
	}

	public Bytes slice(int offset) {
		if (offset >= byteLength) return Bytes.empty;
		return new Bytes(buffer, byteOffset + offset, byteLength - offset);
	}

	public Bytes slice(int offset, int length) {
		if (offset >= byteLength) return Bytes.empty;
		return new Bytes(buffer, byteOffset + offset, Math.min(length, byteLength - offset));
	}

	// Returns a slice of the indicated length, which is zero-padded if necessary.
	public Bytes zeroPaddedSlice(int offset, int length) {
		if (offset + length > byteLength) {
			Bytes slice = new Bytes(length);
			if (offset < byteLength) System.arraycopy(buffer, byteOffset + offset, slice.buffer, 0, byteLength - offset);
			return slice;
		}

		return new Bytes(buffer, byteOffset + offset, length);
	}

	public void set(int offset, ByteBuffer byteBuffer) {
		System.arraycopy(byteBuffer.array(), byteBuffer.arrayOffset(), buffer, byteOffset + offset, byteBuffer.limit() - createByteBuffer().arrayOffset());
	}

	public void set(int offset, Bytes bytes) {
		System.arraycopy(bytes.buffer, bytes.byteOffset, buffer, byteOffset + offset, bytes.byteLength);
	}

	public void set(int offset, byte[] byteArray) {
		System.arraycopy(byteArray, 0, buffer, byteOffset + offset, byteArray.length);
	}

	public byte getInteger8(int offset) {
		return buffer[byteOffset + offset];
	}

	public short getInteger16(int offset) {
		int value = (int) buffer[byteOffset + offset] & 0xff;
		value <<= 8;
		value |= (int) buffer[byteOffset + offset + 1] & 0xff;
		return (short) value;
	}

	public int getInteger32(int offset) {
		int value = (int) buffer[byteOffset + offset] & 0xff;
		value <<= 8;
		value |= (int) buffer[byteOffset + offset + 1] & 0xff;
		value <<= 8;
		value |= (int) buffer[byteOffset + offset + 2] & 0xff;
		value <<= 8;
		value |= (int) buffer[byteOffset + offset + 3] & 0xff;
		return value;
	}

	public long getInteger64(int offset) {
		long value = (long) buffer[byteOffset + offset] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 1] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 2] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 3] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 4] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 5] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 6] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 7] & 0xff;
		return value;
	}

	public short getUnsigned8(int offset) {
		return (short) (buffer[byteOffset + offset] & 0xff);
	}

	public int getUnsigned16(int offset) {
		int value = (int) buffer[byteOffset + offset] & 0xff;
		value <<= 8;
		value |= (int) buffer[byteOffset + offset + 1] & 0xff;
		return value;
	}

	public long getUnsigned32(int offset) {
		long value = (long) buffer[byteOffset + offset] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 1] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 2] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 3] & 0xff;
		return value;
	}

	public long getUnsigned64(int offset) {
		long value = (long) buffer[byteOffset + offset] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 1] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 2] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 3] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 4] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 5] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 6] & 0xff;
		value <<= 8;
		value |= (long) buffer[byteOffset + offset + 7] & 0xff;
		return value;
	}

	public void setInteger8(int offset, byte value) {
		buffer[byteOffset + offset] = value;
	}

	public void setInteger16(int offset, short value) {
		buffer[byteOffset + offset] = (byte) (value >> 8);
		buffer[byteOffset + offset + 1] = (byte) value;
	}

	public void setInteger32(int offset, int value) {
		buffer[byteOffset + offset] = (byte) (value >> 24);
		buffer[byteOffset + offset + 1] = (byte) (value >> 16);
		buffer[byteOffset + offset + 2] = (byte) (value >> 8);
		buffer[byteOffset + offset + 3] = (byte) value;
	}

	public void setInteger64(int offset, long value) {
		buffer[byteOffset + offset] = (byte) (value >> 56);
		buffer[byteOffset + offset + 1] = (byte) (value >> 48);
		buffer[byteOffset + offset + 2] = (byte) (value >> 40);
		buffer[byteOffset + offset + 3] = (byte) (value >> 32);
		buffer[byteOffset + offset + 4] = (byte) (value >> 24);
		buffer[byteOffset + offset + 5] = (byte) (value >> 16);
		buffer[byteOffset + offset + 6] = (byte) (value >> 8);
		buffer[byteOffset + offset + 7] = (byte) value;
	}

	public void setUnsigned8(int offset, short value) {
		buffer[byteOffset + offset] = (byte) (value & 0xff);
	}

	public void setUnsigned16(int offset, int value) {
		buffer[byteOffset + offset] = (byte) (value >> 8);
		buffer[byteOffset + offset + 1] = (byte) value;
	}

	public void setUnsigned32(int offset, long value) {
		buffer[byteOffset + offset] = (byte) (value >> 24);
		buffer[byteOffset + offset + 1] = (byte) (value >> 16);
		buffer[byteOffset + offset + 2] = (byte) (value >> 8);
		buffer[byteOffset + offset + 3] = (byte) value;
	}

	public void setUnsigned64(int offset, long value) {
		buffer[byteOffset + offset] = (byte) (value >> 56);
		buffer[byteOffset + offset + 1] = (byte) (value >> 48);
		buffer[byteOffset + offset + 2] = (byte) (value >> 40);
		buffer[byteOffset + offset + 3] = (byte) (value >> 32);
		buffer[byteOffset + offset + 4] = (byte) (value >> 24);
		buffer[byteOffset + offset + 5] = (byte) (value >> 16);
		buffer[byteOffset + offset + 6] = (byte) (value >> 8);
		buffer[byteOffset + offset + 7] = (byte) value;
	}

	public boolean asBoolean() {
		return byteLength > 0;
	}

	public long asUnsigned() {
		long value = 0;
		for (int i = 0; i < byteLength; i++)
			value = (value << 8) + ((long) buffer[byteOffset + i] & 0xff);
		return value;
	}

	public long asInteger() {
		if (byteLength == 0) return 0;
		long value = (long) buffer[byteOffset] & 0xff;
		if ((value & 0x80) > 0) value -= 256;
		for (int i = 1; i < byteLength; i++)
			value = (value << 8) + ((long) buffer[byteOffset + i] & 0xff);
		return value;
	}

	public String asText() {
		return utf8.decode(createByteBuffer()).toString();
	}

	public String asHex() {
		char[] hex = new char[byteLength * 2];
		for (int i = 0; i < byteLength; i++) {
			hex[i * 2] = hexDigits[(buffer[byteOffset + i] >> 4) & 0xf];
			hex[i * 2 + 1] = hexDigits[buffer[byteOffset + i] & 0xf];
		}
		return new String(hex);
	}

	public void writeToFile(File file) throws IOException {
		FileOutputStream stream = new FileOutputStream(file, false);
		stream.write(buffer, byteOffset, byteLength);
		stream.close();
	}

	public void writeToStream(OutputStream stream) throws IOException {
		stream.write(buffer, byteOffset, byteLength);
	}

	public void invert(int offset, int length) {
		for (int i = 0; i < length; i++) {
			buffer[byteOffset + offset + i] ^= 0xff;
		}
	}

	public Bytes concatenate(Bytes bytes) {
		Bytes result = new Bytes(byteLength + bytes.byteLength);
		result.set(0, this);
		result.set(byteLength, bytes);
		return result;
	}

	public Bytes concatenate(ByteBuffer bytes) {
		Bytes result = new Bytes(byteLength + bytes.limit() - bytes.arrayOffset());
		result.set(0, this);
		result.set(byteLength, bytes);
		return result;
	}

	public Bytes clone() {
		Bytes result = new Bytes(byteLength);
		result.set(0, this);
		return result;
	}

	@Override
	public boolean equals(Object that) {
		return that instanceof Bytes && equals((Bytes) that);
	}

	public boolean equals(Bytes that) {
		if (that == null) return false;
		if (that == this) return true;

		// Check the length
		if (byteLength != that.byteLength) return false;

		// Check the content
		for (int i = 0; i < byteLength; i++)
			if (buffer[byteOffset + i] != that.buffer[that.byteOffset + i])
				return false;
		return true;
	}

	@Override
	public int hashCode() {
		// Rotating hash on the first 16 bytes only. This is not particularly good, but good enough.
		// For better hash functions, see http://burtleburtle.net/bob/hash/doobs.html
		int length = byteLength < 16 ? byteLength : 16;
		int hashCode = byteLength;
		for (int i = 0; i < length; i++)
			hashCode = (hashCode << 12) ^ ((hashCode & 0xfff00000) >> 20) ^ buffer[byteOffset + i];
		return hashCode;
	}

	@Override
	public int compareTo(@NonNull Bytes that) {
		for (int i = 0; i < byteLength && i < that.byteLength; i++) {
			int b0 = buffer[byteOffset + i] & 0xff;
			int b1 = that.buffer[that.byteOffset + i] & 0xff;
			if (b0 < b1) return -1;
			if (b0 > b1) return 1;
		}
		if (byteLength < that.byteLength) return -1;
		if (byteLength > that.byteLength) return 1;
		return 0;
	}

	public boolean startsWith(Bytes bytes) {
		if (bytes.byteLength > byteLength) return false;
		for (int i = 0; i < bytes.byteLength; i++)
			if (buffer[byteOffset + i] != bytes.buffer[bytes.byteOffset + i]) return false;
		return true;
	}

	public String toString(int maxLength) {
		StringBuilder builder = new StringBuilder();
		int len = byteLength > maxLength ? maxLength - 3 : byteLength;
		for (int i = 0; i < len; i++) {
			int b = buffer[byteOffset + i];
			char c = b >= 32 ? (char) b : '-';
			builder.append(c);
		}
		if (len < byteLength) builder.append("...");
		return builder.toString();
	}

	@Override
	public String toString() {
		return toString(16);
	}
}
