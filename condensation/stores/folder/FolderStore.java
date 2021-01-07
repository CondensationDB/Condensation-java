package condensation.stores.folder;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import condensation.Condensation;
import condensation.actors.KeyPair;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.stores.BoxAddition;
import condensation.stores.BoxLabel;
import condensation.stores.BoxRemoval;
import condensation.stores.Store;
import condensation.tasks.BackgroundTask;

// This object store stores objects locally on the file system.
// This is thread-safe, because no state is kept. All operations are indirectly synchronized through the file system.
public final class FolderStore extends Store {
	public final File objectsFolder;
	public final File accountsFolder;
	public final boolean enforceCompleteness;

	public static FolderStore forUrl(@NonNull String url) {
		if (!url.startsWith("file:///")) return null;
		return new FolderStore(new File(url.substring(7)), true);
	}

	public FolderStore(@NonNull File folder, boolean enforceCompleteness) {
		super("file://" + folder);
		this.objectsFolder = new File(folder, "objects");
		this.accountsFolder = new File(folder, "accounts");
		this.enforceCompleteness = enforceCompleteness;
	}

	public File objectFile(@NonNull Hash hash) {
		String hashHex = hash.hex();
		return new File(new File(objectsFolder, hashHex.substring(0, 2)), hashHex.substring(2));
	}

	// *** Asynchronous interface

	@Override
	public void get(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull GetDone done) {
		new Get(hash, done);
	}

	class Get implements BackgroundTask {
		final Hash hash;
		final GetDone done;
		CondensationObject object;
		String fileSystemError;

		Get(Hash hash, GetDone done) {
			this.hash = hash;
			this.done = done;
			Condensation.fileSystemExecutor.run(this);
		}

		@Override
		public void background() {
			String hashHex = hash.hex();
			File file = new File(new File(objectsFolder, hashHex.substring(0, 2)), hashHex.substring(2));
			try {
				object = CondensationObject.from(file);
			} catch (FileNotFoundException ignored) {
			} catch (IOException e) {
				fileSystemError = e.getMessage();
			}
		}

		@Override
		public void after() {
			if (fileSystemError != null) done.onGetStoreError("Failed to read the object file: " + fileSystemError);
			else if (object == null) done.onGetNotFound();
			else done.onGetDone(object);
		}
	}

	@Override
	public void put(@NonNull Hash hash, @NonNull CondensationObject object, @NonNull KeyPair keyPair, @NonNull PutDone done) {
		new Put(hash, object, done);
	}

	class Put implements BackgroundTask {
		final Hash hash;
		final CondensationObject object;
		final PutDone done;
		String fileSystemError;

		Put(Hash hash, CondensationObject object, PutDone done) {
			this.hash = hash;
			this.object = object;
			this.done = done;
			Condensation.fileSystemExecutor.run(this);
		}

		@Override
		public void background() {
			try {
				writeObject(hash, object);
			} catch (IOException e) {
				fileSystemError = e.getMessage();
			}
		}

		@Override
		public void after() {
			if (fileSystemError == null) done.onPutDone();
			else done.onPutStoreError("Failed to write the object file: " + fileSystemError);
		}
	}

	boolean isComplete(Hash hash, CondensationObject object) {
		if (!enforceCompleteness) return true;

		for (Hash childHash : object.hashes()) {
			String hashHex = childHash.hex();
			File folder = new File(objectsFolder, hashHex.substring(0, 2));
			File file = new File(folder, hashHex.substring(2));
			if (file.exists()) continue;

			Condensation.log("Attempting to write " + hash.hex() + ", but " + childHash.hex() + " is missing.");
		}

		return true;
	}

	private void writeObject(Hash hash, CondensationObject object) throws IOException {
		String hashHex = hash.hex();
		File folder = new File(objectsFolder, hashHex.substring(0, 2));
		File file = new File(folder, hashHex.substring(2));
		if (file.exists()) return;

		if (!isComplete(hash, object)) throw new IOException("Incomplete object.");

		folder.mkdir();
		FileOutputStream stream = new FileOutputStream(file, false);
		stream.write(object.header.buffer, object.header.byteOffset, object.header.byteLength);
		stream.write(object.data.buffer, object.data.byteOffset, object.data.byteLength);
		stream.close();
	}

	@Override
	public void book(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull BookDone done) {
		new Book(hash, done);
	}

	class Book implements BackgroundTask {
		final Hash hash;
		final BookDone done;
		boolean success;

		Book(Hash hash, BookDone done) {
			this.hash = hash;
			this.done = done;
			Condensation.fileSystemExecutor.run(this);
		}

		@Override
		public void background() {
			String hashHex = hash.hex();
			File folder = new File(objectsFolder, hashHex.substring(0, 2));
			File file = new File(folder, hashHex.substring(2));
			if (file.exists()) return;
			success = file.setLastModified(System.currentTimeMillis());
		}

		@Override
		public void after() {
			if (success) done.onBookDone();
			else done.onBookNotFound();
		}
	}

	@Override
	public void list(@NonNull Hash accountHash, @NonNull BoxLabel boxLabel, long timeout, @NonNull KeyPair keyPair, @NonNull final ListDone done) {
		new List(accountHash, boxLabel, done);
	}

	class List implements BackgroundTask {
		final Hash accountHash;
		final BoxLabel boxLabel;
		final ListDone done;
		ArrayList<Hash> result = new ArrayList<>();

		List(Hash accountHash, BoxLabel boxLabel, ListDone done) {
			this.accountHash = accountHash;
			this.boxLabel = boxLabel;
			this.done = done;
			Condensation.fileSystemExecutor.run(this);
		}

		@Override
		public void background() {
			File accountFolder = new File(accountsFolder, accountHash.hex());
			File boxFolder = new File(accountFolder, boxLabel.asText);
			String[] files = boxFolder.list();
			if (files == null) return;
			for (String filename : files) {
				Hash hash = Hash.from(filename);
				if (hash == null) continue;
				result.add(hash);
			}
		}

		@Override
		public void after() {
			done.onListDone(result);
		}
	}

	@Override
	public void modify(@NonNull Collection<BoxAddition> additions, @NonNull Collection<BoxRemoval> removals, @NonNull KeyPair keyPair, @NonNull final ModifyDone done) {
		new Modify(additions, removals, done);
	}

	class Modify implements BackgroundTask {
		final Collection<BoxAddition> additions;
		final Collection<BoxRemoval> removals;
		final ModifyDone done;
		String fileSystemError = null;

		Modify(Collection<BoxAddition> additions, Collection<BoxRemoval> removals, ModifyDone done) {
			this.additions = additions;
			this.removals = removals;
			this.done = done;
			Condensation.fileSystemExecutor.run(this);
		}

		@Override
		public void background() {
			// Process additions
			for (BoxAddition addition : additions) {
				File accountFolder = new File(accountsFolder, addition.accountHash.hex());
				accountFolder.mkdir();
				File boxFolder = new File(accountFolder, addition.boxLabel.asText);
				boxFolder.mkdir();
				File file = new File(boxFolder, addition.hash.hex());

				try {
					if (addition.object != null) writeObject(addition.hash, addition.object);
					Bytes.empty.writeToFile(file);
				} catch (IOException e) {
					fileSystemError = e.getMessage();
					return;
				}
			}

			// Process removals
			for (BoxRemoval removal : removals) {
				File accountFolder = new File(accountsFolder, removal.accountHash.hex());
				File boxFolder = new File(accountFolder, removal.boxLabel.asText);
				new File(boxFolder, removal.hash.hex()).delete();
			}
		}

		@Override
		public void after() {
			if (fileSystemError != null) done.onModifyStoreError("Failed to write the box entry file: " + fileSystemError);
			else done.onModifyDone();
		}
	}

	// Store administration functions

	// Creates the store if it does not exist. The store folder itself must exist.
	public boolean createIfNecessary() {
		accountsFolder.mkdir();
		objectsFolder.mkdir();
		return accountsFolder.isDirectory() && objectsFolder.isDirectory();
	}

	// Removes an account. This is a best-effort operation, which fails silently.
	public void deleteAccount(Hash accountHash) {
		new DeleteAccount(accountHash);
	}

	class DeleteAccount implements BackgroundTask {
		final Hash accountHash;

		public DeleteAccount(Hash accountHash) {
			this.accountHash = accountHash;
			Condensation.fileSystemExecutor.run(this);
		}

		@Override
		public void background() {
			File accountFolder = new File(accountsFolder, accountHash.hex());
			deleteBox(accountFolder, BoxLabel.MESSAGES);
			deleteBox(accountFolder, BoxLabel.PRIVATE);
			deleteBox(accountFolder, BoxLabel.PUBLIC);
			accountFolder.delete();
		}

		void deleteBox(File accountFolder, BoxLabel boxLabel) {
			File boxFolder = new File(accountFolder, boxLabel.asText);
			File[] files = boxFolder.listFiles();
			if (files != null)
				for (File file : files)
					file.delete();
			boxFolder.delete();
		}

		@Override
		public void after() {
		}
	}
}
