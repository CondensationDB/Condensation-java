package condensation.tools;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

import condensation.Condensation;
import condensation.serialization.Bytes;

public class Misc {
	public static String interpret(Bytes bytes) {
		if (bytes.byteLength == 0) return "(empty)";

		try {
			CharsetDecoder charsetDecoder = Bytes.utf8.newDecoder();
			charsetDecoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
			CharBuffer text = charsetDecoder.decode(bytes.createByteBuffer());
			return text.toString();
		} catch (CharacterCodingException ignore) {
		}

		if (bytes.byteLength > 16) return bytes.slice(0, 16).asHex() + "...";
		return bytes.asHex();
	}

	public static String interpretInteger(Bytes bytes, long now) {
		if (bytes.byteLength > 6) return null;
		long integer = bytes.asInteger();
		if (integer > now - 2 * Condensation.YEAR && integer < now + 2 * Condensation.YEAR)
			return relativeTime(integer - now);
		return "" + integer;
	}

	public static String relativeTime(long ms) {
		if (ms < 0) return positiveDuration(-ms) + " ago";
		else if (ms > 0) return "in " + positiveDuration(ms);
		else return "now";
	}

	public static String positiveDuration(long ms) {
		if (ms < 1000) return ms + " ms";

		long seconds = ms / 1000;
		if (seconds < 90) return seconds + " s";

		long minutes = seconds / 60;
		if (minutes < 90) return minutes + " min";

		long hours = minutes / 60;
		if (hours < 30) return hours + " h";

		long days = hours / 24;
		return days + " days";
	}

	public static String byteSize(long size) {
		if (size < 1000) return size + " bytes";
		if (size < 10000) return String.format("%.2f", (float) size / 1000) + " KB";
		if (size < 100000) return String.format("%.1f", (float) size / 1000) + " KB";
		if (size < 1000000) return String.format("%.0f", (float) size / 1000) + " KB";
		if (size < 10000000) return String.format("%.2f", (float) size / 1000000) + " MB";
		if (size < 100000000) return String.format("%.1f", (float) size / 1000000) + " MB";
		return String.format("%.0f", (float) size / 1000000) + " MB";
	}
}
