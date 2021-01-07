package condensation.stores.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.KeyPair;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;

public abstract class Request implements Runnable, Comparable<Request> {
	protected final RequestQueue queue;

	// For inspection purposes only
	final String function;
	final String parameter;
	long enqueued = 0L;
	long sent = 0L;
	long received = 0L;
	int responseCode = 0;

	public Request(RequestQueue queue, String function, String parameter) {
		this.queue = queue;
		this.function = function;
		this.parameter = parameter;
	}

	protected void ready() {
		enqueued = System.currentTimeMillis();
		queue.requests.add(this);
		Condensation.mainThread.post(queue);
	}

	@Override
	public void run() {
		try {
			long start = System.currentTimeMillis();
			background();
			final long duration = System.currentTimeMillis() - start;

			Condensation.mainThread.post(new Runnable() {
				@Override
				public void run() {
					if (duration > 2000) Condensation.log(function + " request took " + duration + " ms, " + queue.storeState.storeUrl);

					try {
						after();
					} catch (Throwable th) {
						Condensation.logError("HTTPStoreQueue.Request", th);
					}

					received = System.currentTimeMillis();
					queue.executingRequest = null;
					queue.run();
				}
			});
		} catch (Throwable th) {
			Condensation.logError("HTTPStoreQueue.Request", th);
		}
	}

	protected abstract void storeDisabled();

	protected abstract boolean before();

	protected abstract void background();

	protected abstract void after();

	@Override
	public int compareTo(@NonNull Request that) {
		return Condensation.longCompare(enqueued, that.enqueued);
	}

	public static HttpURLConnection openConnection(String url) throws IOException {
		URL requestUrl = new URL(url);
		HttpURLConnection connection = (HttpURLConnection) requestUrl.openConnection();
		connection.setConnectTimeout(HTTPStoreManager.connectTimeout);
		connection.setReadTimeout(HTTPStoreManager.readTimeout);
		return connection;
	}

	// Reads an input stream completely into memory.
	public static Bytes readStream(InputStream inputStream) throws IOException {
		// Read all chunks
		ArrayList<Bytes> chunks = new ArrayList<>();

		int chunkSize = 256 * 1024;
		int chunkRead = 0;
		byte[] buffer = new byte[chunkSize];
		while (true) {
			int read = inputStream.read(buffer, chunkRead, chunkSize - chunkRead);
			if (read < 0) break;

			chunkRead += read;
			if (chunkRead == chunkSize) {
				chunks.add(new Bytes(buffer, 0, chunkRead));
				chunkRead = 0;
				buffer = new byte[chunkSize];
			}
		}

		inputStream.close();
		if (chunkRead > 0) chunks.add(new Bytes(buffer, 0, chunkRead));

		// Concatenate
		return Bytes.concatenate(chunks);
	}

	protected void addSignature(HttpURLConnection connection, KeyPair keyPair, String method, String url, Bytes content) {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.setTimeInMillis(System.currentTimeMillis());
		Bytes dateBytes = new Bytes(24);
		setDigits4(dateBytes, 0, cal.get(Calendar.YEAR));
		setLetter(dateBytes, 4, '-');
		setDigits2(dateBytes, 5, cal.get(Calendar.MONTH) + 1);
		setLetter(dateBytes, 7, '-');
		setDigits2(dateBytes, 8, cal.get(Calendar.DAY_OF_MONTH));
		setLetter(dateBytes, 10, 'T');
		setDigits2(dateBytes, 11, cal.get(Calendar.HOUR_OF_DAY));
		setLetter(dateBytes, 13, ':');
		setDigits2(dateBytes, 14, cal.get(Calendar.MINUTE));
		setLetter(dateBytes, 16, ':');
		setDigits2(dateBytes, 17, cal.get(Calendar.SECOND));
		setLetter(dateBytes, 19, '.');
		setLetter(dateBytes, 20, '0');
		setLetter(dateBytes, 21, '0');
		setLetter(dateBytes, 22, '0');
		setLetter(dateBytes, 23, 'Z');

		Bytes nullByte = new Bytes(0);
		String urlStart = url.substring(0, 8).toLowerCase();
		String host = urlStart.startsWith("http://") ? url.substring(7) : urlStart.equals("https://") ? url.substring(8) : url;
		Bytes bytesToSign = content == null ?
				Bytes.concatenate(dateBytes, nullByte, Bytes.fromText(method), nullByte, Bytes.fromText(host)) :
				Bytes.concatenate(dateBytes, nullByte, Bytes.fromText(method), nullByte, Bytes.fromText(host), nullByte, content);
		Hash hashToSign = Hash.calculateFor(bytesToSign);
		Bytes signature = keyPair.sign(hashToSign);

		connection.setRequestProperty("Condensation-Date", dateBytes.asText());
		connection.setRequestProperty("Condensation-Actor", keyPair.publicKey.hash.hex());
		connection.setRequestProperty("Condensation-Signature", signature.asHex());
	}

	private void setDigits2(Bytes bytes, int offset, int value) {
		bytes.buffer[bytes.byteOffset + offset + 1] = (byte) (value % 10 + 48);
		value /= 10;
		bytes.buffer[bytes.byteOffset + offset] = (byte) (value % 10 + 48);
	}

	private void setDigits4(Bytes bytes, int offset, int value) {
		bytes.buffer[bytes.byteOffset + offset + 3] = (byte) (value % 10 + 48);
		value /= 10;
		bytes.buffer[bytes.byteOffset + offset + 2] = (byte) (value % 10 + 48);
		value /= 10;
		bytes.buffer[bytes.byteOffset + offset + 1] = (byte) (value % 10 + 48);
		value /= 10;
		bytes.buffer[bytes.byteOffset + offset] = (byte) (value % 10 + 48);
	}

	private void setLetter(Bytes bytes, int offset, char c) {
		bytes.buffer[bytes.byteOffset + offset] = (byte) c;
	}
}

