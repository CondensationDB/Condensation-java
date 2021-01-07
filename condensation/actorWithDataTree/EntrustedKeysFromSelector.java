package condensation.actorWithDataTree;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;
import condensation.actors.Actor;
import condensation.actors.EntrustedActor;
import condensation.actors.EntrustedKeysProvider;
import condensation.actors.GetPublicKey;
import condensation.actors.PublicKey;
import condensation.dataTree.Selector;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.tasks.AwaitCounter;

public class EntrustedKeysFromSelector implements EntrustedKeysProvider {
	public final Actor actor;
	public final Selector selector;

	// State
	private final HashMap<Bytes, PublicKey> cachedEntrustedKeys = new HashMap<>();

	public EntrustedKeysFromSelector(Actor actor, Selector selector) {
		this.actor = actor;
		this.selector = selector;
	}

	public void entrust(EntrustedActor entrustedActor) {
		Selector child = selector.child(entrustedActor.publicKey.hash.bytes.slice(0, 16));
		child.addObject(entrustedActor.publicKey.hash, entrustedActor.publicKey.object);

		Record record = new Record();
		record.add(BC.hash).add(entrustedActor.publicKey.hash);
		record.add(BC.store).add(entrustedActor.storeUrl);
		child.set(record);
	}

	public void doNotEntrust(Hash hash) {
		selector.child(hash.bytes.slice(0, 16)).clear();
	}

	@Override
	public void getEntrustedKeys(EntrustedKeysProvider.Done done) {
		new GetEntrustedActors(done);
	}

	class GetEntrustedActors implements AwaitCounter.Done {
		final EntrustedKeysProvider.Done done;
		final AwaitCounter awaitCounter = new AwaitCounter();
		final ArrayList<PublicKey> entrustedKeys = new ArrayList<>();
		boolean hasError = false;

		GetEntrustedActors(EntrustedKeysProvider.Done done) {
			this.done = done;

			for (Selector child : selector.children()) {
				Record record = child.record();
				Hash hash = record.child(BC.hash).hashValue();

				// Remove
				if (hash == null || !hash.bytes.startsWith(child.label)) {
					cachedEntrustedKeys.remove(child.label);
					continue;
				}

				// Keep
				PublicKey entrustedKey = cachedEntrustedKeys.get(child.label);
				if (entrustedKey != null && hash.equals(entrustedKey.hash)) {
					entrustedKeys.add(entrustedKey);
					continue;
				}

				// Add
				new LoadPublicKey(child, hash);
			}

			awaitCounter.then(this);
		}

		class LoadPublicKey implements GetPublicKey.Done {
			final Selector selector;

			LoadPublicKey(Selector selector, Hash hash) {
				this.selector = selector;
				awaitCounter.await();
				new GetPublicKey(hash, selector.dataTree.unsaved, actor.keyPair, this);
			}

			@Override
			public void onGetPublicKeyDone(@NonNull PublicKey publicKey) {
				cachedEntrustedKeys.put(selector.label, publicKey);
				entrustedKeys.add(publicKey);
				awaitCounter.done();
			}

			@Override
			public void onGetPublicKeyInvalid(@NonNull String reason) {
				awaitCounter.done();
			}

			@Override
			public void onGetPublicKeyStoreError(@NonNull String error) {
				hasError = true;
				awaitCounter.done();
			}
		}

		@Override
		public void onAwaitCounterDone() {
			if (hasError) done.onGetEntrustedKeysFailed();
			else done.onGetEntrustedKeysDone(entrustedKeys);
		}
	}
}
