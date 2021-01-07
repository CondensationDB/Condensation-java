package condensation.tools;

import java.util.ArrayList;

import condensation.actors.KeyPair;
import condensation.serialization.Hash;
import condensation.stores.BoxLabel;
import condensation.stores.Store;

public class AccountInspection extends Inspection {
	final KeyPair keyPair;
	final Store store;
	final String storeUrl;
	final Hash accountHash;
	final ArrayList<Inspection> boxInspections = new ArrayList<>();

	public AccountInspection(CondensationView view, KeyPair keyPair, Store store, String storeUrl, Hash accountHash) {
		super(view);
		this.keyPair = keyPair;
		this.store = store;
		this.storeUrl = storeUrl;
		this.accountHash = accountHash;
		this.sortKey = accountHash.bytes;
		setLines(3);
	}

	@Override
	public void onOpen() {
		boxInspections.add(new BoxInspection(view, keyPair, store, accountHash, BoxLabel.MESSAGES));
		boxInspections.add(new BoxInspection(view, keyPair, store, accountHash, BoxLabel.PRIVATE));
		boxInspections.add(new BoxInspection(view, keyPair, store, accountHash, BoxLabel.PUBLIC));
	}

	@Override
	public void onClose() {
		boxInspections.clear();
	}

	@Override
	public void update() {
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.title("Account");
		drawer.text(storeUrl);
		drawer.text(accountHash.shortHex());
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
