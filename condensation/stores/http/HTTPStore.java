package condensation.stores.http;

import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.ImmutableList;
import condensation.actors.KeyPair;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.BC;
import condensation.stores.BoxAddition;
import condensation.stores.BoxLabel;
import condensation.stores.BoxRemoval;
import condensation.stores.Store;

public class HTTPStore extends Store {
	public static HTTPStore forUrl(@NonNull String url) {
		if (url.startsWith("http://") || url.startsWith("https://")) return new HTTPStore(url);
		return null;
	}

	public HTTPStore(String url) {
		super(url);
	}

	@Override
	public void get(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull GetDone done) {
		HTTPStoreState storeState = HTTPStoreManager.getOrCreate(id);
		new Get(storeState.getQueue, hash, done);
	}

	class Get extends Request {
		final Hash hash;
		final GetDone done;
		CondensationObject object;
		String error = null;

		Get(RequestQueue queue, Hash hash, GetDone done) {
			super(queue, "GET", hash.shortHex());
			this.hash = hash;
			this.done = done;
			ready();
		}

		@Override
		protected void storeDisabled() {
			done.onGetStoreError("Store disabled.");
		}

		@Override
		protected boolean before() {
			return true;
		}

		@Override
		protected void background() {
			try {
				// Prepare the request
				HttpURLConnection connection = openConnection(id + "/objects/" + hash.hex());
				connection.setRequestMethod("GET");
				connection.setDoInput(true);

				// Check the response code
				responseCode = connection.getResponseCode();
				if (responseCode != 200 && responseCode != 204) {
					if (responseCode != 404) error = "The server replied with a " + responseCode + " HTTP response code.";
					return;
				}

				// Read the returned object
				Bytes bytes = readStream(connection.getInputStream());
				object = CondensationObject.from(bytes);
			} catch (UnknownHostException | ConnectException e) {
				error = e.toString();
			} catch (Exception e) {
				Condensation.logError("HTTPStore exception in GET " + id + " " + hash.shortHex(), e);
				error = e.toString();
			}
		}

		@Override
		protected void after() {
			queue.storeState.updateReachability(error);

			if (error != null) done.onGetStoreError(error);
			else if (object != null) done.onGetDone(object);
			else done.onGetNotFound();
		}
	}


	@Override
	public void put(@NonNull Hash hash, @NonNull CondensationObject object, @NonNull KeyPair keyPair, @NonNull PutDone done) {
		// Schedule small objects and large objects on different threads
		HTTPStoreState storeState = HTTPStoreManager.getOrCreate(id);
		RequestQueue queue = object.byteLength() < 50000 ? storeState.putSmallQueue : storeState.putLargeQueue;
		new Put(queue, hash, object, keyPair, done);
	}

	class Put extends Request {
		final Hash hash;
		final CondensationObject object;
		final KeyPair keyPair;
		final PutDone done;
		String error = null;

		Put(RequestQueue queue, Hash hash, CondensationObject object, KeyPair keyPair, PutDone done) {
			super(queue, "PUT", hash.shortHex() + " " + object.byteLength() + " bytes");
			this.hash = hash;
			this.object = object;
			this.keyPair = keyPair;
			this.done = done;
			ready();
		}

		@Override
		protected void storeDisabled() {
			done.onPutStoreError("Store disabled.");
		}

		@Override
		protected boolean before() {
			// If the object has been booked recently, there is nothing to do
			if (queue.storeState.isBooked(hash)) {
				done.onPutDone();
				return false;
			}

			return true;
		}

		@Override
		protected void background() {
			try {
				// Prepare the request
				HttpURLConnection connection = openConnection(id + "/objects/" + hash.hex());
				connection.setRequestProperty("Content-Type", "application/condensation-object");
				connection.setRequestMethod("PUT");
				connection.setDoOutput(true);
				// TODO: add signature

				// Send the data
				OutputStream out = connection.getOutputStream();
				out.write(object.header.buffer, object.header.byteOffset, object.header.byteLength);
				out.write(object.data.buffer, object.data.byteOffset, object.data.byteLength);
				out.close();

				// Check the response code
				responseCode = connection.getResponseCode();
				if (responseCode != 200 && responseCode != 204) error = "The server replied with a " + responseCode + " HTTP response code.";
			} catch (UnknownHostException | ConnectException e) {
				error = e.toString();
			} catch (Exception e) {
				Condensation.logError("HTTPStore exception in PUT " + id + " " + hash.shortHex(), e);
				error = e.toString();
			}
		}

		@Override
		protected void after() {
			if (error == null) queue.storeState.setBooked(hash);
			queue.storeState.updateReachability(error);
			if (error != null) done.onPutStoreError(error);
			else done.onPutDone();
		}
	}

	@Override
	public void book(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull BookDone done) {
		HTTPStoreState storeState = HTTPStoreManager.getOrCreate(id);
		new Book(storeState.putSmallQueue, hash, keyPair, done);
	}

	class Book extends Request {
		final Hash hash;
		final KeyPair keyPair;
		final BookDone done;
		boolean booked = false;
		String error = null;

		Book(RequestQueue queue, Hash hash, KeyPair keyPair, BookDone done) {
			super(queue, "BOOK", hash.shortHex());
			this.hash = hash;
			this.keyPair = keyPair;
			this.done = done;
			ready();
		}

		@Override
		protected void storeDisabled() {
			done.onBookStoreError("Store disabled.");
		}

		@Override
		protected boolean before() {
			// If the object has been booked recently, there is nothing to do
			if (queue.storeState.isBooked(hash)) {
				done.onBookDone();
				return false;
			}

			return true;
		}

		@Override
		protected void background() {
			try {
				// Prepare the request
				HttpURLConnection connection = openConnection(id + "/objects/" + hash.hex());
				connection.setRequestMethod("POST");
				// TODO: add signature

				// Check the response code
				responseCode = connection.getResponseCode();
				if (responseCode == 200 || responseCode == 204) booked = true;
				else if (responseCode == 404) booked = false;
				else error = "The server replied with a " + responseCode + " HTTP response code.";
			} catch (UnknownHostException | ConnectException e) {
				error = e.toString();
			} catch (Exception e) {
				Condensation.logError("HTTPStore exception in BOOK " + id + " " + hash.shortHex(), e);
				error = e.toString();
			}
		}

		@Override
		protected void after() {
			if (error == null && booked) queue.storeState.setBooked(hash);
			queue.storeState.updateReachability(error);
			if (error != null) done.onBookStoreError(error);
			else if (booked) done.onBookDone();
			else done.onBookNotFound();
		}
	}

	@Override
	public void list(@NonNull Hash accountHash, @NonNull BoxLabel boxLabel, long timeout, @NonNull KeyPair keyPair, @NonNull ListDone done) {
		HTTPStoreState storeState = HTTPStoreManager.getOrCreate(id);
		new List(storeState.getQueue, accountHash, boxLabel, 0, keyPair, done);
	}

	public void watch(@NonNull Hash accountHash, @NonNull BoxLabel boxLabel, long watch, @NonNull KeyPair keyPair, @NonNull ListDone done) {
		HTTPStoreState storeState = HTTPStoreManager.getOrCreate(id);
		new List(storeState.getQueue, accountHash, boxLabel, watch, keyPair, done);
	}

	class List extends Request {
		final Hash accountHash;
		final BoxLabel boxLabel;
		final long watch;
		final KeyPair keyPair;
		final ListDone done;
		ArrayList<Hash> hashes = new ArrayList<>();
		String error = null;

		List(RequestQueue queue, Hash accountHash, BoxLabel boxLabel, long watch, KeyPair keyPair, ListDone done) {
			super(queue, "LIST", accountHash.shortHex() + "/" + boxLabel.asText);
			this.accountHash = accountHash;
			this.boxLabel = boxLabel;
			this.watch = watch;
			this.keyPair = keyPair;
			this.done = done;
			ready();
		}

		@Override
		protected void storeDisabled() {
			done.onListStoreError("Store disabled.");
		}

		@Override
		protected boolean before() {
			return true;
		}

		@Override
		protected void background() {
			try {
				// Prepare the request
				HttpURLConnection connection = openConnection(id + "/accounts/" + accountHash.hex() + "/" + boxLabel.asText);
				connection.setRequestMethod("GET");
				if (watch > 0) connection.setRequestProperty("Condensation-Watch", watch + " ms");
				connection.setDoInput(true);

				// Check the response code
				responseCode = connection.getResponseCode();
				if (responseCode != 200 && responseCode != 204) {
					error = "The server replied with a " + responseCode + " HTTP response code.";
					return;
				}

				// Read the hashes
				Bytes bytes = readStream(connection.getInputStream());
				for (int i = 0; i < bytes.byteLength - 31; i += 32)
					hashes.add(Hash.from(bytes.slice(i, 32)));
			} catch (UnknownHostException | ConnectException | SocketTimeoutException e) {
				error = e.toString();
			} catch (Exception e) {
				Condensation.logError("HTTPStore exception in LIST " + id + " " + accountHash.shortHex() + " " + boxLabel.asText, e);
				error = e.toString();
			}
		}

		@Override
		protected void after() {
			queue.storeState.updateReachability(error);
			if (error != null) done.onListStoreError(error);
			else done.onListDone(hashes);
		}
	}

	@Override
	public void modify(@NonNull Collection<BoxAddition> additions, @NonNull Collection<BoxRemoval> removals, @NonNull KeyPair keyPair, @NonNull ModifyDone done) {
		HTTPStoreState storeState = HTTPStoreManager.getOrCreate(id);
		new Modify(storeState.putSmallQueue, additions, removals, keyPair, done);
	}

	class Modify extends Request {
		final ImmutableList<BoxAddition> additions;
		final ImmutableList<BoxRemoval> removals;
		final KeyPair keyPair;
		final ModifyDone done;
		String error = null;

		Modify(RequestQueue queue, Collection<BoxAddition> additions, Collection<BoxRemoval> removals, KeyPair keyPair, ModifyDone done) {
			super(queue, "MODIFY", additions.size() + "+ | " + removals.size() + "-");
			this.additions = ImmutableList.sorted(additions);
			this.removals = ImmutableList.sorted(removals);
			this.keyPair = keyPair;
			this.done = done;
			ready();
		}

		@Override
		protected void storeDisabled() {
			done.onModifyStoreError("Store disabled.");
		}

		@Override
		protected boolean before() {
			return true;
		}

		@Override
		protected void background() {
			String url = id + "/accounts";
			Bytes bytes = recordFromBoxOperations(additions, removals).toObject().toBytes();

			try {
				// Prepare the request
				HttpURLConnection connection = openConnection(url);
				connection.setRequestProperty("Content-Type", "application/condensation-box-modification");
				connection.setRequestMethod("POST");
				connection.setDoOutput(true);
				if (needSignature()) addSignature(connection, keyPair, "POST", url, bytes);

				// Send the message
				OutputStream out = connection.getOutputStream();
				out.write(bytes.buffer, bytes.byteOffset, bytes.byteLength);
				out.close();

				// Check the response code
				responseCode = connection.getResponseCode();
				if (responseCode != 200 && responseCode != 204) error = "The server replied with a " + responseCode + " HTTP response code.";
			} catch (UnknownHostException | ConnectException e) {
				error = e.toString();
			} catch (Exception e) {
				Condensation.logError("HTTPStore exception in MODIFY " + id, e);
				error = e.toString();
			}
		}

		boolean needSignature() {
			if (removals.length > 0) return true;
			Hash me = keyPair.publicKey.hash;
			for (BoxAddition addition : additions) {
				if (addition.boxLabel != BoxLabel.MESSAGES) return true;
				if (addition.accountHash.equals(me)) return true;
			}

			// Message additions on foreign accounts don't need to be signed
			return false;
		}

		@Override
		protected void after() {
			queue.storeState.updateReachability(error);
			if (error != null) done.onModifyStoreError(error);
			else done.onModifyDone();
		}
	}

	static Record recordFromBoxOperations(Iterable<BoxAddition> additions, Iterable<BoxRemoval> removals) {
		Record record = new Record();

		// Envelopes
		Record envelopesRecord = record.add(BC.envelopes);
		HashSet<Hash> envelopesAdded = new HashSet<>();

		// Process additions
		Record addRecord = record.add(BC.add);
		Record accountRecord = null;
		Record boxRecord = null;
		for (BoxAddition addition : additions) {
			if (accountRecord == null || !accountRecord.bytes.equals(addition.accountHash.bytes)) {
				accountRecord = addRecord.add(addition.accountHash.bytes);
				boxRecord = null;
			}

			if (boxRecord == null || !boxRecord.bytes.equals(addition.boxLabel.asBytes))
				boxRecord = accountRecord.add(addition.boxLabel.asBytes);

			boxRecord.add(addition.hash.bytes);
			if (addition.object != null && !envelopesAdded.contains(addition.hash)) {
				envelopesRecord.add(addition.hash.bytes).add(addition.object.toBytes());
				envelopesAdded.add(addition.hash);
			}
		}

		// Process removals
		Record removeRecord = record.add(BC.remove);
		accountRecord = null;
		boxRecord = null;
		for (BoxRemoval removal : removals) {
			if (accountRecord == null || !accountRecord.bytes.equals(removal.accountHash.bytes)) {
				accountRecord = removeRecord.add(removal.accountHash.bytes);
				boxRecord = null;
			}

			if (boxRecord == null || !boxRecord.bytes.equals(removal.boxLabel.asBytes))
				boxRecord = accountRecord.add(removal.boxLabel.asBytes);

			boxRecord.add(removal.hash.bytes);
		}

		return record;
	}

	// This is a non-standard extension to delete accounts.
	public void delete(Hash accountHash, KeyPair keyPair, DeleteDone done) {
		HTTPStoreState storeState = HTTPStoreManager.getOrCreate(id);
		new Delete(storeState.putSmallQueue, accountHash, keyPair, done);
	}

	public class Delete extends Request {
		final Hash accountHash;
		final KeyPair keyPair;
		final DeleteDone done;
		String error = null;

		public Delete(RequestQueue queue, Hash accountHash, KeyPair keyPair, DeleteDone done) {
			super(queue, "DELETE", accountHash.shortHex());
			this.accountHash = accountHash;
			this.keyPair = keyPair;
			this.done = done;
			ready();
		}

		@Override
		protected void storeDisabled() {
			done.onDeleteStoreError("Store disabled.");
		}

		@Override
		protected boolean before() {
			return true;
		}

		@Override
		protected void background() {
			try {
				// Prepare the request
				HttpURLConnection connection = openConnection(id + "/accounts/" + accountHash.hex());
				connection.setRequestMethod("DELETE");
				// TODO: add signature

				// Check the response code
				responseCode = connection.getResponseCode();
				if (responseCode != 200 && responseCode != 204) error = "The server replied with a " + responseCode + " HTTP response code.";
			} catch (UnknownHostException | ConnectException e) {
				error = e.toString();
			} catch (Exception e) {
				Condensation.logError("HTTPStore exception in DELETE " + id + " " + accountHash.shortHex(), e);
				error = e.toString();
			}
		}

		@Override
		protected void after() {
			queue.storeState.updateReachability(error);
			if (error == null) done.onDeleteDone();
			else done.onDeleteStoreError(error);
		}
	}

	public interface DeleteDone {
		void onDeleteDone();

		void onDeleteStoreError(String error);
	}
}
