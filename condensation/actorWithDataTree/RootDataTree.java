package condensation.actorWithDataTree;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import condensation.actors.MergeableData;
import condensation.actors.PrivateRoot;
import condensation.actors.Source;
import condensation.dataTree.DataTree;
import condensation.dataTree.Part;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.stores.MissingObject;
import condensation.stores.Store;
import condensation.stores.Transfer;

// The data tree.
public final class RootDataTree extends DataTree implements MergeableData {
	public final PrivateRoot privateRoot;
	public final Bytes label;

	// The current data sharing message
	//Record dataSharingMessage = null;

	public RootDataTree(PrivateRoot privateRoot, Bytes label) {
		super(privateRoot.privateBoxReader.keyPair, privateRoot.unsaved);
		this.privateRoot = privateRoot;
		this.label = label;
		privateRoot.addDataHandler(label, this);
	}

	@Override
	public void savingDone(long revision, Part newPart, ArrayList<Part> obsoleteParts) {
		privateRoot.unsaved.state.merge(unsaved.savingState);
		privateRoot.dataChanged();
		unsaved.savingDone();

		// If there are changes, save the private root and share the new data
		if (obsoleteParts.size() == 0 && newPart == null) return;
	}

	public String toString() {
		return "RootDataTree(" + label.asText() + ")";
	}

	@Override
	public void addDataTo(Record record) {
		for (Part part : parts.values())
			record.add(part.hashAndKey);
	}

	@Override
	public void mergeData(Record record) {
		// Read each object mentioned in the record
		// Note that we must merge them at the same time, since only the whole set represents a consistent state.
		ArrayList<HashAndKey> hashesAndKeys = new ArrayList<>();
		for (Record child : record.children)
			hashesAndKeys.add(child.asHashAndKey());
		merge(hashesAndKeys);
	}

	@Override
	public void mergeExternalData(Store store, Record record, Source source) {
		new MergeExternalData(store, record, source);
	}

	class MergeExternalData implements Transfer.Done {
		final Source source;
		final ArrayList<HashAndKey> hashesAndKeys = new ArrayList<>();

		MergeExternalData(Store store, Record record, Source source) {
			this.source = source;
			if (source != null) source.keep();

			// Read each object mentioned in the record
			// Note that we must merge them at the same time, since only the whole set represents a consistent state.
			ArrayList<Hash> hashes = new ArrayList<>();
			for (Record child : record.children) {
				HashAndKey hashAndKey = child.asHashAndKey();
				if (hashAndKey == null) continue;
				hashes.add(hashAndKey.hash);
				hashesAndKeys.add(hashAndKey);
			}

			keyPair.transfer(hashes, store, privateRoot.unsaved, this);
		}

		@Override
		public void onTransferDone() {
			merge(hashesAndKeys);
			if (source != null) privateRoot.unsaved.state.addMergedSource(source);
		}

		@Override
		public void onTransferMissingObject(@NonNull MissingObject missingObject) {
			missingObject.report();
			source.discard();
		}

		@Override
		public void onTransferStoreError(@NonNull Store store, @NonNull String error) {
			source.discard();
		}
	}
}
