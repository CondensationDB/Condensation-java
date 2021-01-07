package condensation.tools;

import androidx.annotation.NonNull;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.serialization.RecordReader;
import condensation.stores.GetAndDecrypt;
import condensation.stores.Store;

public class EncryptedObjectInspection extends ObjectInspection {
	final HashAndKey hashAndKey;

	public EncryptedObjectInspection(CondensationView view, KeyPair keyPair, Store store, HashAndKey hashAndKey) {
		super(view, keyPair, store);
		this.hashAndKey = hashAndKey;
		sortKey = hashAndKey.hash.bytes;
		setTitle("Encrypted object", hashAndKey.hash);
		load();
	}

	@Override
	void loadResult() {
		new Load();
	}

	class Load implements GetAndDecrypt.Done {
		Load() {
			new GetAndDecrypt(hashAndKey, store, keyPair, this);
		}

		@Override
		public void onGetAndDecryptDone(@NonNull CondensationObject object) {
			Record record = new Record();
			RecordReader recordReader = new RecordReader(object);
			recordReader.readChildren(record);
			boolean hasError = recordReader.hasError || recordReader.trailer().byteLength > 0;
			setResult(new Result(object, hasError ? null : record));
			if (!hasError) setTitle("Encrypted record", hashAndKey.hash);
		}

		@Override
		public void onGetAndDecryptInvalid(@NonNull String reason) {
			setError(reason);
		}

		@Override
		public void onGetAndDecryptStoreError(@NonNull String error) {
			setError(error);
		}
	}
}
