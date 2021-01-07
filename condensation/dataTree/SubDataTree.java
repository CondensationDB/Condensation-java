package condensation.dataTree;

import java.util.ArrayList;

import condensation.Condensation;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;

public final class SubDataTree extends DataTree implements BranchListener, DataTree.ReadDone {
	public final Selector parentSelector;
	private boolean isDataAvailable = false;

	public SubDataTree(Selector parentSelector) {
		super(parentSelector.dataTree.keyPair, parentSelector.dataTree.unsaved);
		this.parentSelector = parentSelector;
	}

	public String toString() {
		return parentSelector.toString();
	}

	private Selector partSelector(HashAndKey hashAndKey) {
		return parentSelector.child(hashAndKey.hash.bytes.slice(0, 16));
	}

	// Attaches the sub data tree to its parent data tree. Changes are read and merged automatically until you call "detach".
	public void attach() {
		parentSelector.trackBranch(this);
		onBranchChanged(Selector.none);
	}

	// Detaches the sub data tree from the parent data tree, Changes are not merged any more. Unless you keep a reference to it, the SubDataTree will be garbage collected.
	public void detach() {
		parentSelector.untrackBranch(this);
	}

	@Override
	public void onBranchChanged(Iterable<Selector> selectors) {
		// Note that we must merge them all at the same time, since only the combination of all parts represents a consistent state.
		ArrayList<HashAndKey> hashesAndKeys = new ArrayList<>();
		for (Selector child : parentSelector.children())
			hashesAndKeys.add(child.hashAndKeyValue());
		merge(hashesAndKeys);
		read(this);
	}

	@Override
	public void savingDone(long revision, Part newPart, ArrayList<Part> obsoleteParts) {
		parentSelector.dataTree.unsaved.state.merge(unsaved.savingState);

		// Remove obsolete parts
		for (Part part : obsoleteParts)
			partSelector(part.hashAndKey).merge(revision, new Record());

		// Add the new part
		if (newPart != null) {
			Record record = new Record();
			record.add(newPart.hashAndKey);
			partSelector(newPart.hashAndKey).merge(revision, record);
		}

		unsaved.savingDone();
	}

	@Override
	public void onDataTreeReadDone() {
		isDataAvailable = true;
	}

	@Override
	public void onDataTreeReadFailed() {
		// This is not ideal, but reading sub data trees from the local store should not fail.
		Condensation.mainThread.postDelayed(new Runnable() {
			@Override
			public void run() {
				read(SubDataTree.this);
			}
		}, Condensation.SECOND * 10);
	}

	public boolean isDataAvailable() {
		return isDataAvailable;
	}
}
