package condensation.stores.folder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.stores.MissingObject;
import condensation.tasks.BackgroundTask;
import condensation.tasks.BackgroundThread;
import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;

public class FolderStoreInspection extends Inspection {
	final FolderStore store;
	final String label;
	final long garbageCollectionGraceInterval;

	final ArrayList<Inspection> items = new ArrayList<>();
	final HashMap<Hash, FolderStoreAccountInspection> inspections = new HashMap<>();

	boolean counting = false;
	String line0 = "";
	String line1 = "";

	public FolderStoreInspection(CondensationView view, FolderStore store, String label, long garbageCollectionGraceInterval) {
		super(view);
		this.store = store;
		this.label = label;
		this.garbageCollectionGraceInterval = garbageCollectionGraceInterval;
		setLines(3);
	}

	@Override
	public void update() {
		view.itemsChanged();
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.title(label + " (folder store)");
		drawer.text(line0);
		drawer.text(line1);
		drawer.option(0, "GC", view.style.centeredGrayText);
		drawer.option(1, "COUNT", view.style.centeredGrayText);
	}

	@Override
	public void onClick(float x, float y) {
		if (hitTestOption(x, y, 0)) {
			garbageCollection.start();
		} else if (hitTestOption(x, y, 1)) {
			count();
		} else {
			view.open(this);
		}
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		boolean hasChanges = false;

		// Prepare the items to remove
		HashSet<Hash> toRemove = new HashSet<>(inspections.keySet());

		// Add new items
		String[] files = store.accountsFolder.list();
		if (files == null) files = new String[0];
		for (String filename : files) {
			Hash hash = Hash.from(filename);
			if (hash == null) continue;

			FolderStoreAccountInspection inspection = inspections.get(hash);
			if (inspection == null) {
				inspections.put(hash, new FolderStoreAccountInspection(view, store, hash));
				hasChanges = true;
			} else {
				toRemove.remove(hash);
			}
		}

		// Remove obsolete items
		for (Hash key : toRemove) {
			inspections.remove(key);
			hasChanges = true;
		}

		// Rebuild the list if necessary
		if (hasChanges) {
			items.clear();
			items.addAll(inspections.values());
			Collections.sort(items);
		}

		return items;
	}

	void setResult(String line0, String line1) {
		this.line0 = line0;
		this.line1 = line1;
	}

	void count() {
		if (counting) return;
		counting = true;
		setResult("Counting ...", "");
		update();
		new BackgroundThread(new Count());

	}

	class Count implements BackgroundTask {
		private int objectsCount = 0;
		private int otherCount = 0;
		private long objectsSize = 0L;
		private long otherSize = 0L;

		@Override
		public void background() {
			File baseFolder = store.objectsFolder;
			String[] subFolders = baseFolder.list();
			if (subFolders == null) subFolders = new String[0];

			for (String subFolderName : subFolders) {
				if (subFolderName.length() != 2) continue;
				if (Bytes.fromHex(subFolderName) == null) continue;

				File subFolder = new File(baseFolder, subFolderName);
				String[] files = subFolder.list();
				if (files == null) continue;

				for (String filename : files) {
					if (filename.equals(".") || filename.equals("..")) continue;
					File file = new File(subFolder, filename);
					Hash hash = Hash.from(subFolderName + filename);
					if (hash == null) {
						otherCount += 1;
						otherSize += file.length();
					} else {
						objectsCount += 1;
						objectsSize += file.length();
					}
				}
			}
		}

		@Override
		public void after() {
			counting = false;
			setResult(objectsCount + " objects, " + String.format("%1.2f", objectsSize / (double) (1024 * 1024)) + " MiB", otherCount + " other files, " + String.format("%1.2f", otherSize / (double) (1024 * 1024)) + " MiB");
			update();
		}
	}

	final GarbageCollection garbageCollection = new GarbageCollection();

	class GarbageCollection implements CollectGarbage.Done {
		boolean running = false;

		// Incremented from the garbage collection thread, ready on the main thread upon done
		int missingObjects = 0;
		int invalidObjects = 0;
		int invalidFiles = 0;

		void start() {
			if (running) return;
			running = true;
			setResult("Collecting garbage ...", "");
			missingObjects = 0;
			invalidObjects = 0;
			invalidFiles = 0;
			new CollectGarbage(store, garbageCollectionGraceInterval, this);
		}

		@Override
		public void onCollectGarbageMissingObjectAsync(MissingObject missingObject) {
			missingObjects += 1;
		}

		@Override
		public void onCollectGarbageInvalidObjectAsync(Hash hash) {
			invalidObjects += 1;
		}

		@Override
		public void onCollectGarbageInvalidFileAsync(File file) {
			invalidFiles += 1;
		}

		@Override
		public void onCollectGarbageDone(boolean success, int keptObject, int deletedObjects) {
			running = false;
			setResult(success ? "Garbage collected" : "Garbage collection failed", keptObject + " kept, " + deletedObjects + " deleted" + (missingObjects == 0 ? "" : ", " + missingObjects + " missing") + (invalidObjects == 0 ? "" : ", " + invalidObjects + " invalid") + (invalidFiles == 0 ? "" : ", " + invalidFiles + " other files"));
		}
	}
}
