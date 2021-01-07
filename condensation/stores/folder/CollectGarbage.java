package condensation.stores.folder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

import condensation.Condensation;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.stores.BoxLabel;
import condensation.stores.MissingObject;
import condensation.tasks.BackgroundTask;
import condensation.tasks.BackgroundThread;

public final class CollectGarbage implements BackgroundTask {
	final FolderStore folderStore;
	final long graceTime;
	final Done done;

	// State
	final ArrayList<Hash> existing = new ArrayList<>();
	final HashSet<Hash> toKeep = new HashSet<>();
	int deletedObjects;
	boolean success;

	public CollectGarbage(FolderStore folderStore, long graceInterval, final Done done) {
		this.folderStore = folderStore;
		this.done = done;
		Condensation.assertMainThread();

		// Don't delete files that are younger than 24 hours (to keep partially written or synchronized trees)
		this.graceTime = System.currentTimeMillis() - graceInterval;

		// Go through the files in the background
		new BackgroundThread(this);
	}

	@Override
	public void background() {
		// Traverse the accounts
		success = traverseAccounts();
		if (!success) return;

		// Traverse objects younger than 24 h
		success = traverseObjects();
		if (!success) return;

		// Delete those remaining
		success = delete();
	}

	boolean traverseAccounts() {
		File baseFolder = folderStore.accountsFolder;
		String[] subFolders = baseFolder.list();
		if (subFolders == null) subFolders = new String[0];

		for (String subFolderName : subFolders) {
			File accountFolder = new File(baseFolder, subFolderName);
			Hash accountHash = Hash.from(subFolderName);
			if (accountHash == null) {
				done.onCollectGarbageInvalidFileAsync(accountFolder);
				continue;
			}

			if (!traverseBox(accountFolder, BoxLabel.MESSAGES)) return false;
			if (!traverseBox(accountFolder, BoxLabel.PRIVATE)) return false;
			if (!traverseBox(accountFolder, BoxLabel.PUBLIC)) return false;

			String[] files = accountFolder.list();
			if (files == null) continue;
			if (files.length < 1) accountFolder.delete();
		}

		return true;
	}

	boolean traverseBox(File accountFolder, BoxLabel boxLabel) {
		File boxFolder = new File(accountFolder, boxLabel.asText);
		String context = "garbage collection, " + accountFolder.getName() + "/" + boxLabel.asText;

		String[] files = boxFolder.list();
		if (files == null) return true;
		if (files.length < 1) {
			boxFolder.delete();
			return true;
		}

		for (String filename : files) {
			Hash hash = Hash.from(filename);
			if (hash == null) {
				done.onCollectGarbageInvalidFileAsync(new File(boxFolder, filename));
				continue;
			}

			if (!traverse(hash, new ArrayDeque<Hash>(), context)) return false;
		}

		return true;
	}

	boolean traverseObjects() {
		File baseFolder = folderStore.objectsFolder;
		String[] subFolders = baseFolder.list();
		if (subFolders == null) subFolders = new String[0];

		for (String subFolderName : subFolders) {
			File subFolder = new File(baseFolder, subFolderName);
			if (subFolderName.length() != 2 || Bytes.fromHex(subFolderName) == null) {
				done.onCollectGarbageInvalidFileAsync(subFolder);
				continue;
			}

			String[] files = subFolder.list();
			if (files == null) continue;

			for (String filename : files) {
				File file = new File(subFolder, filename);
				Hash hash = Hash.from(subFolderName + filename);
				if (hash == null) {
					done.onCollectGarbageInvalidFileAsync(file);
					continue;
				}

				existing.add(hash);
				if (toKeep.contains(hash)) continue;
				long lm = file.lastModified();
				if (lm < graceTime) continue;
				if (!traverse(hash, new ArrayDeque<Hash>(), "garbage collection, recent objects")) return false;
			}
		}

		return true;
	}

	// Returns the hashes in the header of an object. This method basically exists for performance: we could get the
	// whole object, and read the hashes, but it's more efficient to read just the header. Since we are on a local
	// storage system, we do not need to verify the object's integrity.
	boolean traverse(Hash hash, ArrayDeque<Hash> path, String context) {
		// Ignore if we have checked this hash before
		if (toKeep.contains(hash)) return true;
		toKeep.add(hash);

		// If the object does not exist, there is nothing we can do
		File file = folderStore.objectFile(hash);
		if (!file.exists()) {
			MissingObject missingObject = new MissingObject(hash, folderStore);
			missingObject.path.addAll(path);
			missingObject.context = context;
			done.onCollectGarbageMissingObjectAsync(missingObject);
			return true;
		}

		FileInputStream stream;
		try {
			stream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			return true;
		}

		try {
			// Read the number of hashes, and calculate the header length
			byte[] count = Condensation.read(stream, 4);
			if (count == null) {
				stream.close();
				return true;
			}

			int countHashes = new Bytes(count).getInteger32(0);  //   (count[0] << 24) | (count[1] << 16) | (count[2] << 8) | count[3]
			int hashesLength = countHashes * 32;

			// If the object is not a valid Condensation object, we pretend it has no hashes
			int size = (int) file.length();
			if (hashesLength < 0 || 4 + hashesLength > size) {
				stream.close();
				done.onCollectGarbageInvalidObjectAsync(hash);
				return true;
			}

			// Read the hashes
			byte[] bytes = Condensation.read(stream, hashesLength);
			stream.close();
			if (bytes == null) return true;

			// Traverse all hashes
			path.addLast(hash);
			for (int i = 0; i < countHashes; i++)
				if (!traverse(Hash.from(new Bytes(bytes, i * 32, 32)), path, context)) return false;
			path.removeLast();


			return true;
		} catch (IOException e) {
			// If anything fails, we stop garbage collection
			try {
				stream.close();
			} catch (IOException ignored) {
			}

			return false;
		}
	}

	boolean delete() {
		boolean allOk = true;
		for (Hash hash : existing) {
			if (toKeep.contains(hash)) continue;
			allOk &= folderStore.objectFile(hash).delete();
			deletedObjects += 1;
		}

		return allOk;
	}

	@Override
	public void after() {
		int keptObjects = existing.size() - deletedObjects;
		done.onCollectGarbageDone(success, keptObjects, deletedObjects);
	}

	public interface Done {
		void onCollectGarbageMissingObjectAsync(MissingObject missingObject);

		void onCollectGarbageInvalidObjectAsync(Hash hash);

		void onCollectGarbageInvalidFileAsync(File file);

		void onCollectGarbageDone(boolean success, int keptObject, int deletedObjects);
	}
}
