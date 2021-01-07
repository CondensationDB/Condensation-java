package condensation.stores.folder;

import java.io.File;
import java.util.ArrayList;

import condensation.serialization.Hash;
import condensation.stores.BoxLabel;
import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;

class FolderStoreBoxInspection extends Inspection {
	final FolderStore store;
	final Hash accountHash;
	final BoxLabel boxLabel;
	final File boxFolder;

	// State
	boolean isOpen = false;
	int count = 0;
	final ArrayList<String> hashes = new ArrayList<>();

	FolderStoreBoxInspection(CondensationView view, FolderStore store, Hash accountHash, BoxLabel boxLabel) {
		super(view);
		this.store = store;
		this.accountHash = accountHash;
		this.boxLabel = boxLabel;
		this.sortKey = boxLabel.asBytes;

		boxFolder = new File(new File(store.accountsFolder, accountHash.hex()), boxLabel.asText);
		update();
	}

	public void onOpen() {
		isOpen = true;
	}

	@Override
	public void update() {
		hashes.clear();
		String[] files = boxFolder.list();

		if (files != null) {
			for (String filename : files) {
				Hash hash = Hash.from(filename);
				if (hash == null) continue;
				hashes.add(filename);
			}
		}

		count = hashes.size();
		setLines(1 + hashes.size());
		view.itemsChanged();
	}

	public void updateCount() {
		if (isOpen) return;
		String[] files = boxFolder.list();
		count = files == null ? 0 : files.length;
	}

	public void onClose() {
		hashes.clear();
		isOpen = false;
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.title(boxLabel.asText + " (" + hashes.size() + ")");
		for (String hash : hashes)
			drawer.text(hash);
		drawer.option(0, "CLR", view.style.centeredRedText);
	}

	@Override
	public void onClick(float x, float y) {
		if (hitTestOption(x, y, 0)) {
			File[] files = boxFolder.listFiles();
			if (files == null) return;
			for (File file : files) file.delete();
			view.invalidate();
			update();
		}
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return noChildren;
	}
}
