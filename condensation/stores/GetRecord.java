package condensation.stores;

import androidx.annotation.NonNull;

import condensation.Condensation;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.tasks.BackgroundTask;

public class GetRecord implements BackgroundTask, Store.GetDone {
	final Done done;

	private CondensationObject object = null;
	private Record record = null;

	public GetRecord(Hash hash, Store store, KeyPair keyPair, Done done) {
		this.done = done;
		store.get(hash, keyPair, this);
	}

	@Override
	public void onGetDone(@NonNull CondensationObject object) {
		this.object = object;
		Condensation.computationExecutor.run(this);
	}

	@Override
	public void onGetNotFound() {
		done.onGetRecordInvalid("Not found.");
	}

	@Override
	public void onGetStoreError(@NonNull String error) {
		done.onGetRecordStoreError(error);
	}

	@Override
	public void background() {
		record = Record.from(object);
	}

	@Override
	public void after() {
		if (record == null) done.onGetRecordInvalid("Not a record.");
		else done.onGetRecordDone(record, object);
	}

	public interface Done {
		void onGetRecordDone(@NonNull Record record, @NonNull CondensationObject object);

		void onGetRecordInvalid(@NonNull String reason);

		void onGetRecordStoreError(@NonNull String error);
	}
}
