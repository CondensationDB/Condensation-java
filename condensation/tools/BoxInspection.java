package condensation.tools;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import condensation.actors.KeyPair;
import condensation.serialization.Hash;
import condensation.stores.BoxLabel;
import condensation.stores.Store;

class BoxInspection extends Inspection {
	final KeyPair keyPair;
	final Store store;
	final Hash accountHash;
	final BoxLabel boxLabel;

	// State
	boolean requestRunning = false;
	boolean isOpen = false;
	String error = null;
	ArrayList<Hash> hashes = null;
	ArrayList<Inspection> children = new ArrayList<>();

	BoxInspection(CondensationView view, KeyPair keyPair, Store store, Hash accountHash, BoxLabel boxLabel) {
		super(view);
		this.keyPair = keyPair;
		this.store = store;
		this.accountHash = accountHash;
		this.boxLabel = boxLabel;
		this.sortKey = boxLabel.asBytes;
		setLines(2);
		new Update();
	}

	public void onOpen() {
		isOpen = true;
		recreateChildren();
	}

	@Override
	public void update() {
	}

	public void onClose() {
		children.clear();
		isOpen = false;
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.title(boxLabel.asText);
		if (error != null)
			drawer.text(error, view.style.orangeText);
		else if (hashes != null)
			drawer.text(hashes.size() + " envelopes");

		drawer.option(0, requestRunning ? "⋯" : "RELOAD", view.style.centeredGrayText);
	}

	@Override
	public void onClick(float x, float y) {
		if (hitTestOption(x, y, 0))
			new Update();
		else
			view.open(this);
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return children;
	}

	class Update implements Store.ListDone {
		Update() {
			if (requestRunning) return;
			requestRunning = true;
			store.list(accountHash, boxLabel, 0L, keyPair, this);
		}

		@Override
		public void onListDone(ArrayList<Hash> newHashes) {
			requestRunning = false;
			hashes = newHashes;
			view.invalidate();
			recreateChildren();
		}

		@Override
		public void onListStoreError(@NonNull String newError) {
			requestRunning = false;
			hashes = null;
			error = newError;
			view.invalidate();
			recreateChildren();
		}
	}

	void recreateChildren() {
		if (!isOpen) return;
		children.clear();
		if (hashes == null) return;
		for (Hash hash : hashes) {
			if (boxLabel == BoxLabel.PUBLIC)
				children.add(new PublicEnvelopeInspection(view, keyPair, store, accountHash, hash));
			else if (boxLabel == BoxLabel.PRIVATE)
				children.add(new PrivateEnvelopeInspection(view, keyPair, store, accountHash, hash));

			if (children.size() > 10) return;
		}
		view.update();
	}
}
