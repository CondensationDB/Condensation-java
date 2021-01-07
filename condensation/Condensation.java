package condensation;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.concurrent.Executors;

import condensation.actors.BC;
import condensation.actors.PublicKey;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.MissingObjectReporter;
import condensation.tasks.BackgroundExecutor;

public final class Condensation {
	public static long mainThreadId = 0L;
	public static Handler mainThread = null;

	public static BackgroundExecutor computationExecutor = null;
	public static BackgroundExecutor fileSystemExecutor = null;
	public static final String versionString = "Condensation, Java on Android, 2020-06-18";
	public static final Bytes versionBytes = Bytes.fromText(versionString);
	public static final java.util.Random secureRandom = new SecureRandom();
	public static final MissingObjectReporter missingObjectReporter = new MissingObjectReporter();
	public static final Hash emptyBytesHash = Hash.from("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

	// Call this once from the main thread when your app is starting.
	public static void initialize() {
		if (mainThread != null) return;
		mainThreadId = Thread.currentThread().getId();
		mainThread = new Handler();
		computationExecutor = new BackgroundExecutor(Executors.newSingleThreadExecutor());
		fileSystemExecutor = new BackgroundExecutor(Executors.newSingleThreadExecutor());
	}

	public static void assertMainThread() {
		if (Thread.currentThread().getId() == mainThreadId) return;
		logError("Running on asynchronous (instead of the main thread)");
		Log.e("Twelve", "assertAsynchronous", new Throwable());
	}

	public static void assertAsynchronous() {
		if (Thread.currentThread().getId() != mainThreadId) return;
		logError("Running on the main thread (instead of asynchronous)");
	}

	public static long max3(long a, long b, long c) {
		long m = a > b ? a : b;
		return m > c ? m : c;
	}

	public static long max4(long a, long b, long c, long d) {
		long u = a > b ? a : b;
		long v = c > d ? c : d;
		return u > v ? u : v;
	}

	public static int booleanCompare(boolean a, boolean b) {
		return a == b ? 0 : a ? 1 : -1;
	}

	// Long.compare(a, b) which exists only in newer APIs
	public static int longCompare(long a, long b) {
		return a < b ? -1 : a > b ? 1 : 0;
	}

	// *** Random

	public static byte[] randomByteArray(int length) {
		byte[] bytes = new byte[length];
		secureRandom.nextBytes(bytes);
		return bytes;
	}

	public static Bytes randomBytes(int length) {
		return new Bytes(randomByteArray(length));
	}

	// *** Envelopes

	public static boolean verifyEnvelopeSignature(Record envelope, PublicKey publicKey, Hash hash) {
		// Read the signature
		Bytes signature = envelope.child(BC.signature).bytesValue();
		if (signature.byteLength < 1) return false;

		// Verify the signature
		return publicKey.verify(hash, signature);
	}

	// *** Logging (only for debugging)

	public static void log(final String text) {
		Log.i("Condensation", text);
	}

	public static void logError(String text) {
		Log.e("Condensation", text, new Throwable());
	}

	public static void logError(String text, Throwable throwable) {
		Log.e("Condensation", text, throwable);
	}

	private static long performanceStart = 0L;

	public static void startPerformanceMeasurement() {
		performanceStart = System.currentTimeMillis();
	}

	public static void logPerformance(String text) {
		log(text + " " + (System.currentTimeMillis() - performanceStart) + " ms");
	}

	// *** Duration

	public static final long SECOND = 1000L;
	public static final long MINUTE = 60L * 1000L;
	public static final long HOUR = 60L * 60L * 1000L;
	public static final long DAY = 24L * 60L * 60L * 1000L;
	public static final long WEEK = 7L * 24L * 60L * 60L * 1000L;
	public static final long MONTH = 30L * 24L * 60L * 60L * 1000L;
	public static final long YEAR = 365L * 24L * 60L * 60L * 1000L;

	// *** Streams

	// Read length bytes from the stream.
	public static byte[] read(InputStream stream, int length) throws IOException {
		int read = 0;
		byte[] buffer = new byte[length];
		while (read < length) {
			int len = stream.read(buffer, read, length - read);
			if (len < 0) return null;
			read += len;
		}

		return buffer;
	}

	// To run tests
	static {
		//new condensation.tests.Numbers().run();
	}
}
