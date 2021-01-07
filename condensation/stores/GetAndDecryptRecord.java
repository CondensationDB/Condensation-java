package condensation.stores;

import androidx.annotation.NonNull;

import condensation.Condensation;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.tasks.BackgroundTask;

public class GetAndDecryptRecord implements BackgroundTask, Store.GetDone {
	public final HashAndKey hashAndKey;
	final Done done;

	private CondensationObject object = null;
	private Record record = null;

	public GetAndDecryptRecord(HashAndKey hashAndKey, Store store, KeyPair keyPair, Done done) {
		this.hashAndKey = hashAndKey;
		this.done = done;
		store.get(hashAndKey.hash, keyPair, this);
	}

	@Override
	public void onGetDone(@NonNull CondensationObject object) {
		this.object = object;
		Condensation.computationExecutor.run(this);
	}

	@Override
	public void onGetNotFound() {
		done.onGetAndDecryptRecordInvalid("Not found.");
	}

	@Override
	public void onGetStoreError(@NonNull String error) {
		done.onGetAndDecryptRecordStoreError(error);
	}

	@Override
	public void background() {
		object = object.crypt(hashAndKey.key);
		record = Record.from(object);
	}

	@Override
	public void after() {
		if (record == null) done.onGetAndDecryptRecordInvalid("Not a record.");
		else done.onGetAndDecryptRecordDone(record, object);
	}

	public interface Done {
		void onGetAndDecryptRecordDone(@NonNull Record record, @NonNull CondensationObject object);

		void onGetAndDecryptRecordInvalid(@NonNull String reason);

		void onGetAndDecryptRecordStoreError(@NonNull String error);
	}
}
