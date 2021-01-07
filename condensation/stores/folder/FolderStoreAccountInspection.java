package condensation.stores.folder;

import java.util.ArrayList;

import condensation.serialization.Hash;
import condensation.stores.BoxLabel;
import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;

class FolderStoreAccountInspection extends Inspection {
	final FolderStore store;
	final Hash accountHash;
	final FolderStoreBoxInspection messageBoxInspection;
	final FolderStoreBoxInspection privateBoxInspection;
	final FolderStoreBoxInspection publicBoxInspection;
	final ArrayList<Inspection> boxInspections = new ArrayList<>();

	FolderStoreAccountInspection(CondensationView view, FolderStore store, Hash accountHash) {
		super(view);
		this.store = store;
		this.accountHash = accountHash;
		this.sortKey = accountHash.bytes;

		messageBoxInspection = new FolderStoreBoxInspection(view, store, accountHash, BoxLabel.MESSAGES);
		privateBoxInspection = new FolderStoreBoxInspection(view, store, accountHash, BoxLabel.PRIVATE);
		publicBoxInspection = new FolderStoreBoxInspection(view, store, accountHash, BoxLabel.PUBLIC);

		boxInspections.add(messageBoxInspection);
		boxInspections.add(privateBoxInspection);
		boxInspections.add(publicBoxInspection);

		setLines(2);
		update();
	}

	@Override
	public void update() {
		messageBoxInspection.updateCount();
		privateBoxInspection.updateCount();
		publicBoxInspection.updateCount();
		view.itemsChanged();
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.title(accountHash.hex());
		drawer.text(messageBoxInspection.count + " messages, " + privateBoxInspection.count + " private entries, " + publicBoxInspection.count + " public entries");
	}

	@Override
	public void onClick(float x, float y) {
		view.open(this);
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return boxInspections;
	}
}
