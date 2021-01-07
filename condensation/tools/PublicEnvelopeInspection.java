package condensation.tools;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.BC;
import condensation.actors.GetPublicKey;
import condensation.actors.KeyPair;
import condensation.actors.PublicKey;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.Store;

public class PublicEnvelopeInspection extends LoadingInspection<PublicEnvelopeInspection.Result> {
	final Hash hash;
	final Hash accountHash;

	public PublicEnvelopeInspection(CondensationView view, KeyPair keyPair, Store store, Hash accountHash, Hash hash) {
		super(view, keyPair, store);
		this.accountHash = accountHash;
		this.hash = hash;
		sortKey = hash.bytes;
		title = "Public envelope " + hash.shortHex();
		load();
	}

	@Override
	void loadResult() {
		new Load();
	}

	class Load implements Store.GetDone, GetPublicKey.Done {
		Record envelope = null;
		Hash contentHash = null;

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
				setResult(new Result(envelope, null, false, false));
				return;
			}

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
			setResult(new Result(envelope, contentHash, true, signatureOK));
		}

		@Override
		public void onGetPublicKeyInvalid(@NonNull String reason) {
			setResult(new Result(envelope, contentHash, false, false));
		}

		@Override
		public void onGetPublicKeyStoreError(@NonNull String error) {
			setError(error);
		}
	}

	@Override
	int calculateResultLines() {
		if (result.contentHash == null) return result.envelope.countEntries();
		return 3;
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

		drawer.text("Signature", column0, 2, view.style.grayText);
		if (!result.signatureVerified) {
			drawer.text("not verified, public key missing", column1, 2, view.style.orangeText);
		} else if (result.signatureCorrect) {
			drawer.text("correct", column1, 2, view.style.text);
		} else {
			drawer.text("wrong", column1, 2, view.style.orangeText);
		}
	}

	@Override
	void addChildren() {
		if (result.contentHash == null) return;
		children.add(new PlainObjectInspection(view, keyPair, store, result.contentHash));
	}

	class Result {
		final Record envelope;
		final Hash contentHash;
		final boolean signatureVerified;
		final boolean signatureCorrect;

		Result(Record envelope, Hash contentHash, boolean signatureVerified, boolean signatureCorrect) {
			this.envelope = envelope;
			this.contentHash = contentHash;
			this.signatureVerified = signatureVerified;
			this.signatureCorrect = signatureCorrect;
		}
	}
}
