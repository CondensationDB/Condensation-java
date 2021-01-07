package condensation.tools;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.BC;
import condensation.actors.GetPublicKey;
import condensation.actors.KeyPair;
import condensation.actors.PublicKey;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.stores.Store;

public class PrivateEnvelopeInspection extends LoadingInspection<PrivateEnvelopeInspection.Result> {
	final Hash accountHash;
	final Hash hash;

	public PrivateEnvelopeInspection(CondensationView view, KeyPair keyPair, Store store, Hash accountHash, Hash hash) {
		super(view, keyPair, store);
		this.accountHash = accountHash;
		this.hash = hash;
		sortKey = hash.bytes;
		title = "Private envelope " + hash.shortHex();
		load();
	}

	@Override
	void loadResult() {
		new Load();
	}

	class Load implements Store.GetDone, GetPublicKey.Done {
		Record envelope = null;
		Hash contentHash = null;
		Bytes key = null;

		Load() {
			store.get(hash, keyPair, this);
		}

		@Override
		public void onGetDone(@NonNull CondensationObject object) {
			envelope = Record.from(object);
			if (envelope == null) {
				setError("Not a record");
				return;
			}

			contentHash = envelope.child(BC.content).hashValue();
			if (contentHash == null) {
				setResult(new Result(envelope, null, null, false, false));
				return;
			}

			key = keyPair.decryptKeyOnEnvelope(envelope);
			new GetPublicKey(accountHash, store, keyPair, this);
		}

		@Override
		public void onGetNotFound() {
			setError("Not found");
		}

		@Override
		public void onGetStoreError(@NonNull String error) {
			setError(error);
		}

		@Override
		public void onGetPublicKeyDone(@NonNull PublicKey publicKey) {
			boolean signatureOK = Condensation.verifyEnvelopeSignature(envelope, publicKey, contentHash);
			setResult(new Result(envelope, contentHash, key, true, signatureOK));
		}

		@Override
		public void onGetPublicKeyInvalid(@NonNull String reason) {
			setResult(new Result(envelope, contentHash, key, false, false));
		}

		@Override
		public void onGetPublicKeyStoreError(@NonNull String error) {
			setError(error);
		}
	}

	@Override
	int calculateResultLines() {
		if (result.contentHash == null) return result.envelope.countEntries();
		return 4;
	}

	@Override
	void drawResult(Drawer drawer) {
		if (result.contentHash == null) {
			drawer.recordChildren(result.envelope, 0);
			return;
		}

		float column0 = view.style.left;
		float column1 = view.style.left + 60 * view.style.dp;

		drawer.text("Content", column0, 1, view.style.grayText);
		drawer.text(result.contentHash.shortHex(), column1, 1, view.style.text);

		drawer.text("Key", column0, 2, view.style.grayText);
		if (result.key == null) {
			drawer.text("not encrypted for us", column1, 2, view.style.orangeText);
		} else {
			drawer.text("decrypted", column1, 2, view.style.text);
		}

		drawer.text("Signature", column0, 3, view.style.grayText);
		if (!result.signatureVerified) {
			drawer.text("not verified, public key missing", 120, 3, view.style.orangeText);
		} else if (result.signatureCorrect) {
			drawer.text("correct", column1, 3, view.style.text);
		} else {
			drawer.text("wrong", column1, 3, view.style.orangeText);
		}
	}

	@Override
	void addChildren() {
		if (result.contentHash == null) return;
		if (result.key == null) return;
		HashAndKey hashAndKey = new HashAndKey(result.contentHash, result.key);
		children.add(new EncryptedObjectInspection(view, keyPair, store, hashAndKey));
	}

	class Result {
		final Record envelope;
		final Hash contentHash;
		final Bytes key;
		final boolean signatureVerified;
		final boolean signatureCorrect;

		Result(Record envelope, Hash contentHash, Bytes key, boolean signatureVerified, boolean signatureCorrect) {
			this.envelope = envelope;
			this.contentHash = contentHash;
			this.key = key;
			this.signatureVerified = signatureVerified;
			this.signatureCorrect = signatureCorrect;
		}
	}
}
