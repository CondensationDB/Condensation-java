package condensation.actors;

import androidx.annotation.NonNull;

import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.stores.Store;

public class GetPublicKey implements Store.GetDone {
	final Done done;

	public GetPublicKey(Hash hash, Store store, KeyPair keyPair, Done done) {
		this.done = done;
		store.get(hash, keyPair, this);
	}

	@Override
	public void onGetDone(@NonNull CondensationObject object) {
		if (object.byteLength() > 300) done.onGetPublicKeyInvalid("Not a public key.");
		PublicKey publicKey = PublicKey.from(object);
		if (publicKey == null) done.onGetPublicKeyInvalid("Not a public key.");
		else done.onGetPublicKeyDone(publicKey);
	}

	@Override
	public void onGetNotFound() {
		done.onGetPublicKeyInvalid("Not found.");
	}

	@Override
	public void onGetStoreError(@NonNull String error) {
		done.onGetPublicKeyStoreError(error);
	}

	public interface Done {
		void onGetPublicKeyDone(@NonNull PublicKey publicKey);

		void onGetPublicKeyInvalid(@NonNull String reason);

		void onGetPublicKeyStoreError(@NonNull String error);
	}
}
