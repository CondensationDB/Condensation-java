package condensation.stores;

import androidx.annotation.NonNull;

import condensation.Condensation;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.HashAndKey;
import condensation.tasks.BackgroundTask;

public class GetAndDecrypt implements BackgroundTask, Store.GetDone {
	public final HashAndKey hashAndKey;
	final Done done;

	private CondensationObject object = null;

	public GetAndDecrypt(HashAndKey hashAndKey, Store store, KeyPair keyPair, Done done) {
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
		done.onGetAndDecryptInvalid("Not found.");
	}

	@Override
	public void onGetStoreError(@NonNull String error) {
		done.onGetAndDecryptStoreError(error);
	}

	@Override
	public void background() {
		object = object.crypt(hashAndKey.key);
	}

	@Override
	public void after() {
		done.onGetAndDecryptDone(object);
	}

	public interface Done {
		void onGetAndDecryptDone(@NonNull CondensationObject object);

		void onGetAndDecryptInvalid(@NonNull String reason);

		void onGetAndDecryptStoreError(@NonNull String error);
	}
}
