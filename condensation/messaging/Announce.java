package condensation.messaging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import androidx.annotation.NonNull;
import condensation.actorGroups.ActorGroupBuilder;
import condensation.actors.BC;
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

public class Announce {
	public final MessagingStore messagingStore;
	public final Record card = new Record();
	final Unsaved unsaved;
	final ArrayList<RequiredTransfer> transfers = new ArrayList<>();

	public Announce(MessagingStore messagingStore) {
		this.messagingStore = messagingStore;
		unsaved = new Unsaved(messagingStore.store);
		card.add(BC.public_key).add(messagingStore.actor.keyPair.publicKey.hash);
		addObject(messagingStore.actor.keyPair.publicKey.hash, messagingStore.actor.keyPair.publicKey.object);
	}

	public void addObject(@NonNull Hash hash, @NonNull CondensationObject object) {
		unsaved.state.addObject(hash, object);
	}

	public void addTransfer(@NonNull Collection<Hash> hash, @NonNull Store sourceStore, @NonNull String context) {
		transfers.add(new RequiredTransfer(hash, sourceStore, context));
	}

	public void addActorGroup(ActorGroupBuilder actorGroupBuilder) {
		actorGroupBuilder.addToRecord(card, false);
	}

	public void submit(SubmitDone done) {
		new Submit(done);
	}

	class Submit implements Transfer.Done, Store.ListDone, Store.ModifyDone {
		final SubmitDone done;
		final BoxAddition boxAddition;

		int currentTransfer = -1;
		final ArrayList<BoxRemoval> removals = new ArrayList<>();

		Submit(SubmitDone done) {
			this.done = done;

			// Create the public card
			CondensationObject cardObject = card.toObject();
			Hash cardHash = cardObject.calculateHash();
			addObject(cardHash, cardObject);

			// Prepare the public envelope
			Hash me = messagingStore.actor.keyPair.publicKey.hash;
			CondensationObject envelopeObject = messagingStore.actor.keyPair.createPublicEnvelope(cardHash).toObject();
			Hash envelopeHash = envelopeObject.calculateHash();
			boxAddition = new BoxAddition(me, BoxLabel.PUBLIC, envelopeHash, envelopeObject);
			addTransfer(Collections.singleton(cardHash), unsaved, "Announcing");

			// Transfer all trees
			transferNextTree();
		}

		void transferNextTree() {
			currentTransfer += 1;
			if (currentTransfer >= transfers.size()) {
				messagingStore.store.list(messagingStore.actor.keyPair.publicKey.hash, BoxLabel.PUBLIC, 0L, messagingStore.actor.keyPair, this);
				return;
			}

			RequiredTransfer transfer = transfers.get(currentTransfer);
			messagingStore.actor.keyPair.transfer(transfer.hashes, transfer.sourceStore, messagingStore.store, this);
		}

		@Override
		public void onTransferDone() {
			transferNextTree();
		}

		@Override
		public void onTransferMissingObject(@NonNull MissingObject missingObject) {
			missingObject.context = transfers.get(currentTransfer).context;
			missingObject.report();
		}

		@Override
		public void onTransferStoreError(@NonNull Store store, @NonNull String error) {
			failed();
		}

		@Override
		public void onListDone(ArrayList<Hash> hashes) {
			for (Hash hash : hashes)
				removals.add(new BoxRemoval(messagingStore.actor.keyPair.publicKey.hash, BoxLabel.PUBLIC, hash));
			modify();
		}

		@Override
		public void onListStoreError(@NonNull String error) {
			// Ignore errors, in the worst case, we are going to have multiple entries in the public box
			modify();
		}

		void modify() {
			messagingStore.store.modify(Collections.singleton(boxAddition), removals, messagingStore.actor.keyPair, this);
		}

		@Override
		public void onModifyDone() {
			messagingStore.setAnnounced();
			done.onAnnounceDone();
		}

		@Override
		public void onModifyStoreError(@NonNull String error) {
			failed();
		}

		void failed() {
			done.onAnnounceFailed();
		}
	}

	public interface SubmitDone {
		void onAnnounceDone();

		void onAnnounceFailed();
	}
}
