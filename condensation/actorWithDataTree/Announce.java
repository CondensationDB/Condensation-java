package condensation.actorWithDataTree;

import java.util.ArrayList;
import java.util.Collections;

import androidx.annotation.NonNull;
import condensation.actors.Actor;
import condensation.actors.Unsaved;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.BoxAddition;
import condensation.stores.BoxLabel;
import condensation.stores.BoxRemoval;
import condensation.stores.MissingObject;
import condensation.stores.Store;
import condensation.stores.Transfer;
import condensation.tasks.AwaitCounter;

// Creates our card and announces it on all (active) native stores.
public class Announce implements Store.ListDone, AwaitCounter.Done, Store.ModifyDone, Transfer.Done {
	public final Actor actor;
	public final Store destination;
	public final Hash envelopeHash;
	public final Hash cardHash;
	public final CondensationObject envelopeObject;
	public final Done done;

	// State
	BoxAddition boxAddition;
	final AwaitCounter preparation = new AwaitCounter();
	final ArrayList<BoxRemoval> removals = new ArrayList<>();
	boolean hasError = false;

	public Announce(@NonNull Actor actor, Store destination, Record card, @NonNull Done done) {
		this.actor = actor;
		this.destination = destination;
		this.done = done;

		// Create an unsaved state
		Unsaved unsaved = new Unsaved(actor.store);

		// Add the public card and the public key
		Hash me = actor.keyPair.publicKey.hash;
		CondensationObject cardObject = card.toObject();
		cardHash = cardObject.calculateHash();
		unsaved.state.addObject(cardHash, cardObject);
		unsaved.state.addObject(me, actor.keyPair.publicKey.object);

		// Prepare the public envelope
		envelopeObject = actor.keyPair.createPublicEnvelope(cardHash).toObject();
		envelopeHash = envelopeObject.calculateHash();
		boxAddition = new BoxAddition(me, BoxLabel.PUBLIC, envelopeHash, envelopeObject);

		// Upload the objects
		preparation.await();
		actor.keyPair.transfer(Collections.singleton(cardHash), unsaved, destination, this);

		// List the public box
		preparation.await();
		destination.list(me, BoxLabel.PUBLIC, 0L, actor.keyPair, this);

		preparation.then(this);
	}

	@Override
	public void onTransferDone() {
		preparation.done();
	}

	@Override
	public void onTransferMissingObject(@NonNull MissingObject missingObject) {
		hasError = true;
		missingObject.context = "Announce";
		missingObject.report();
	}

	@Override
	public void onTransferStoreError(@NonNull Store store, @NonNull String error) {
		hasError = true;
		done.onAnnounceFailed();
	}

	@Override
	public void onListDone(ArrayList<Hash> hashes) {
		for (Hash hash : hashes)
			removals.add(new BoxRemoval(actor.keyPair.publicKey.hash, BoxLabel.PUBLIC, hash));
		preparation.done();
	}

	@Override
	public void onListStoreError(@NonNull String error) {
		// Ignore errors, in the worst case, we are going to have multiple entries in the public box
		preparation.done();
	}

	@Override
	public void onAwaitCounterDone() {
		if (hasError) return;
		// Modify the public box
		destination.modify(Collections.singleton(boxAddition), removals, actor.keyPair, this);
	}

	@Override
	public void onModifyDone() {
		done.onAnnounceDone(envelopeHash, cardHash);
	}

	@Override
	public void onModifyStoreError(@NonNull String error) {
		done.onAnnounceFailed();
		hasError = true;
	}

	public interface Done {
		void onAnnounceDone(Hash envelopeHash, Hash cardHash);

		void onAnnounceFailed();
	}
}
