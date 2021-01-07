package condensation.tools;

import androidx.annotation.NonNull;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.serialization.RecordReader;
import condensation.stores.Store;

public class PlainObjectInspection extends ObjectInspection {
	final Hash hash;

	public PlainObjectInspection(CondensationView view, KeyPair keyPair, Store store, Hash hash) {
		super(view, keyPair, store);
		this.hash = hash;
		setTitle("Object", hash);
		load();
	}

	@Override
	void loadResult() {
		new Load();
	}

	class Load implements Store.GetDone {
		Load() {
			store.get(hash, keyPair, this);
		}

		@Override
		public void onGetDone(@NonNull CondensationObject object) {
			Record record = new Record();
			RecordReader recordReader = new RecordReader(object);
			recordReader.readChildren(record);
			boolean hasError = recordReader.hasError || recordReader.trailer().byteLength > 0;
			setResult(new PlainObjectInspection.Result(object, hasError ? null : record));
			if (!hasError) setTitle("Record", hash);
		}

		@Override
		public void onGetNotFound() {
			setError("Not found.");
		}

		@Override
		public void onGetStoreError(@NonNull String error) {
			setError(error);
		}
	}
}
