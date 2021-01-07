package condensation.tests;

import condensation.Condensation;
import condensation.serialization.Bytes;

public final class NumberSerializationTest {
	// Tested on 2016-02-11, and no errors found
	public void run() {
		Condensation.log("tests.Unsigned small " + testUnsignedSmall());
		for (int i = 4; i < 80; i++)
			Condensation.log("tests.Unsigned " + i + " --- " + testUnsignedNumber(i) + " --- " + testUnsignedBytes(i));

		Condensation.log("tests.Unsigned small " + testSignedSmall());
		for (int i = 4; i < 80; i++)
			Condensation.log("tests.Signed " + i + " --- " + testPositiveNumber(i) + " --- " + testPositiveBytes(i) + " --- " + testNegativeNumber(i) + " --- " + testNegativeBytes(i));

		Condensation.log("tests.Numbers done");
	}

	String testUnsignedSmall() {
		for (int value = 0; value < 8; value++) {
			long converted = Bytes.fromUnsigned(value).asUnsigned();
			if (converted != value) return converted + " != " + value;
		}
		return "OK";
	}

	String testUnsignedNumber(int bits) {
		for (int r = 0; r < bits; r++) {
			long value = randomNumber(bits);
			long converted = Bytes.fromUnsigned(value).asUnsigned();
			if (converted != value) return converted + " != " + value;
		}
		return "OK";
	}

	String testUnsignedBytes(int bits) {
		for (int r = 0; r < bits; r++) {
			Bytes bytes = randomBytes(bits);
			Bytes converted = Bytes.fromUnsigned(bytes.asUnsigned());
			if (!Bytes.equals(converted, bytes)) return "0x" + converted.asHex() + " != 0x" + bytes.asHex();
		}
		return "OK";
	}

	String testSignedSmall() {
		for (int value = -7; value < 8; value++) {
			long converted = Bytes.fromInteger(value).asInteger();
			if (converted != value) return converted + " != " + value;
		}
		return "OK";
	}

	String testPositiveNumber(int bits) {
		for (int r = 0; r < bits; r++) {
			long value = randomNumber(bits);
			long converted = Bytes.fromInteger(value).asInteger();
			if (converted != value) return converted + " != " + value;
		}
		return "OK";
	}

	String testPositiveBytes(int bits) {
		for (int r = 0; r < bits; r++) {
			Bytes bytes = positiveBytes(randomBytes(bits));
			Bytes converted = Bytes.fromInteger(bytes.asInteger());
			if (!Bytes.equals(converted, bytes)) return "0x" + converted.asHex() + " != 0x" + bytes.asHex();
		}
		return "OK";
	}

	String testNegativeNumber(int bits) {
		for (int r = 0; r < bits; r++) {
			long value = randomNumber(bits);
			long converted = Bytes.fromInteger(value).asInteger();
			if (converted != value) return converted + " != " + value;
		}
		return "OK";
	}

	String testNegativeBytes(int bits) {
		for (int r = 0; r < bits; r++) {
			Bytes bytes = positiveBytes(randomBytes(bits));
			Bytes converted = Bytes.fromInteger(bytes.asInteger());
			if (!Bytes.equals(converted, bytes)) return "0x" + converted.asHex() + " != 0x" + bytes.asHex();
		}
		return "OK";
	}

	long randomNumber(int bits) {
		long value = Condensation.secureRandom.nextLong();
		value |= 0x8000000000000000L;
		if (bits < 64) value >>>= 64 - bits;
		return value;
	}

	Bytes randomBytes(int bits) {
		int byteLength = (bits + 7) / 8;
		Bytes bytes = Condensation.randomBytes(byteLength);
		bytes.buffer[bytes.byteOffset] &= 0xff >> (byteLength * 8 - bits);
		bytes.buffer[bytes.byteOffset] |= 0x80 >> (byteLength * 8 - bits);
		return bytes;
	}

	Bytes positiveBytes(Bytes bytes) {
		if (bytes.buffer[0] >= 0) return bytes;
		Bytes newBytes = new Bytes(bytes.byteLength + 1);
		newBytes.buffer[bytes.byteOffset] = 0;
		newBytes.set(1, bytes);
		return newBytes;
	}

	Bytes negativeBytes(Bytes bytes) {
		Bytes positive = positiveBytes(bytes);
		for (int i = 0; i < bytes.byteLength; i++) bytes.buffer[bytes.byteOffset + i] ^= 0xff;
		return positive;
	}
}
