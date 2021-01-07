package condensation.messaging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import androidx.annotation.NonNull;
import condensation.actorGroups.ActorGroup;
import condensation.actors.ActorOnStore;
import condensation.actors.DataSavedHandler;
import condensation.actors.EntrustedKeysProvider;
import condensation.actors.PublicKey;
import condensation.actors.Unsaved;
import condensation.serialization.Bytes;
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

public class MessageChannel {
	public final MessagingStore messagingStore;
	public final Bytes label;
	public final long validity;

	public final Unsaved unsaved;
	private ArrayList<RequiredTransfer> transfers = new ArrayList<>();
	public Iterable<ActorOnStore> recipients = new ArrayList<>();
	public Iterable<PublicKey> entrustedKeys = new ArrayList<>();
	private final HashSet<Hash> obsoleteHashes = new HashSet<>();
	public Record hints = new Record();

	private int currentSubmissionId = 0;

	public MessageChannel(MessagingStore messagingStore, Bytes label, long validity) {
		this.messagingStore = messagingStore;
		this.label = label;
		this.validity = validity;

		unsaved = new Unsaved(messagingStore.sentList.unsaved);
	}

	public void addObject(@NonNull Hash hash, @NonNull CondensationObject object) {
		unsaved.state.addObject(hash, object);
	}

	public void addTransfer(@NonNull Collection<Hash> hash, @NonNull Store sourceStore, @NonNull String context) {
		transfers.add(new RequiredTransfer(hash, sourceStore, context));
	}

	public void setRecipients(@NonNull ActorGroup actorGroup) {
		ArrayList<ActorOnStore> recipients = new ArrayList<>();
		for (ActorGroup.Member member : actorGroup.members)
			recipients.add(member.actorOnStore);
		this.recipients = recipients;

		ArrayList<PublicKey> entrustedKeys = new ArrayList<>();
		for (ActorGroup.EntrustedActor actor : actorGroup.entrustedActors)
			entrustedKeys.add(actor.actorOnStore.publicKey);
		this.entrustedKeys = entrustedKeys;
	}

	public void setRecipients(@NonNull Iterable<ActorOnStore> recipients, @NonNull Iterable<PublicKey> entrustedKeys) {
		this.recipients = recipients;
		this.entrustedKeys = entrustedKeys;
	}

	public void setEnvelopeHints(Record hints) {
		this.hints = hints;
	}

	public Submission submit(@NonNull Record message, @NonNull final SubmitDone done) {
		return new Submission(message, done);
	}

	SentItem getItem() {
		return messagingStore.sentList.getOrCreate(label);
	}

	public void clear() {
		getItem().clear(System.currentTimeMillis() + validity);
	}

	public class Submission implements MessagingStore.ProcureSentListDone, MessagingStore.CheckIfAnnouncedDone, Transfer.Done, DataSavedHandler, AwaitCounter.Done, EntrustedKeysProvider.Done {
		public final Record message;
		public final SubmitDone done;
		public final int submissionId;
		public final ArrayList<RequiredTransfer> transfers;
		public final Iterable<ActorOnStore> recipients;
		public final Iterable<PublicKey> entrustedKeys;
		public final Record hints;
		public final long expires;

		CondensationObject envelopeObject = null;
		Hash envelopeHash = null;
		int currentTransfer = -1;
		ArrayList<Hash> obsoleteHashesSnapshot;
		final AwaitCounter awaitCounter = new AwaitCounter();
		int succeeded = 0;
		int failed = 0;

		Submission(Record message, SubmitDone done) {
			this.message = message;
			this.done = done;

			currentSubmissionId += 1;
			submissionId = currentSubmissionId;

			this.transfers = MessageChannel.this.transfers;
			MessageChannel.this.transfers = new ArrayList<>();
			this.recipients = MessageChannel.this.recipients;
			this.entrustedKeys = MessageChannel.this.entrustedKeys;
			this.hints = MessageChannel.this.hints;
			this.expires = System.currentTimeMillis() + validity;

			// Load the sent list
			messagingStore.procureSentList(this);
		}

		@Override
		public void onProcureSentListDone() {
			messagingStore.checkIfAnnounced(this);
		}

		@Override
		public void onProcureSentListFailed() {
			done.onMessageChannelSubmitFailed("Unable to load sent list.");
		}

		@Override
		public void onCheckIfAnnouncedDone() {
			messagingStore.entrustedKeysProvider.getEntrustedKeys(this);
		}

		@Override
		public void onCheckIfAnnouncedFailed() {
			done.onMessageChannelSubmitFailed("Not announced on messaging store.");
		}

		@Override
		public void onGetEntrustedKeysDone(Iterable<PublicKey> entrustedKeys) {
			// Create an envelope
			ArrayList<PublicKey> publicKeys = new ArrayList<>();
			publicKeys.add(messagingStore.actor.keyPair.publicKey);
			for (ActorOnStore recipient : recipients) publicKeys.add(recipient.publicKey);
			for (PublicKey entrustedKey : this.entrustedKeys) publicKeys.add(entrustedKey);
			for (PublicKey publicKey : entrustedKeys) publicKeys.add(publicKey);
			Record envelope = messagingStore.actor.keyPair.createMessageEnvelope(messagingStore.storeUrl, message, publicKeys, expires);
			envelope.add(hints.children);
			envelopeObject = envelope.toObject();
			envelopeHash = envelopeObject.calculateHash();

			// Transfer all trees
			transferNextTree();
		}

		@Override
		public void onGetEntrustedKeysFailed() {
			done.onMessageChannelSubmitFailed("Failed to get entrusted keys.");
		}

		void transferNextTree() {
			currentTransfer += 1;
			if (currentTransfer >= transfers.size()) {
				ready();
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
			done.onMessageChannelSubmitFailed("Store " + store.id + " failed: " + error);
		}

		void ready() {
			// Add the current envelope hash to the obsolete hashes
			SentItem item = getItem();
			Hash currentEnvelopeHash = item.getEnvelopeHash();
			if (currentEnvelopeHash != null) obsoleteHashes.add(currentEnvelopeHash);
			this.obsoleteHashesSnapshot = new ArrayList<>(obsoleteHashes);

			// Set the new envelope hash and wait until it gets saved
			//CN.log("MessageChannel submission ready " + expires + " envelope " + envelopeHash.shortHex());
			unsaved.startSaving();
			unsaved.savingState.addDataSavedHandler(this);
			messagingStore.sentList.unsaved.state.merge(unsaved.savingState);
			item.set(expires, envelopeHash, message);
			unsaved.savingDone();
		}

		@Override
		public void onDataSaved() {
			// If we are not the head any more, give up
			if (submissionId != currentSubmissionId) {
				done.onMessageChannelSubmitCancelled();
				return;
			}

			// Process all recipients
			obsoleteHashes.add(envelopeHash);
			for (ActorOnStore recipient : recipients)
				new ProcessRecipient(recipient);
			awaitCounter.then(this);
		}

		class ProcessRecipient implements Store.ModifyDone {
			final ActorOnStore recipient;

			ProcessRecipient(ActorOnStore recipient) {
				this.recipient = recipient;
				awaitCounter.await();

				// Prepare the list of removals
				ArrayList<BoxRemoval> removals = new ArrayList<>();
				for (Hash hash : obsoleteHashesSnapshot)
					removals.add(new BoxRemoval(recipient.publicKey.hash, BoxLabel.MESSAGES, hash));

				// Add the message entry
				BoxAddition addition = new BoxAddition(recipient.publicKey.hash, BoxLabel.MESSAGES, envelopeHash, envelopeObject);
				recipient.store.modify(Collections.singleton(addition), removals, messagingStore.actor.keyPair, this);

				// This modification may run in parallel with other modifications (different version) on the same channel. There is no harm, since the newest version will always survive.
				// Older versions may survive if the store or the network processes requests out-of-order (which Condensation explicitly allows).
			}

			@Override
			public void onModifyDone() {
				succeeded += 1;
				done.onMessageChannelSubmitRecipientDone(recipient);
				awaitCounter.done();
			}

			@Override
			public void onModifyStoreError(@NonNull String error) {
				failed += 1;
				done.onMessageChannelSubmitRecipientFailed(recipient);
				awaitCounter.done();
			}
		}

		@Override
		public void onAwaitCounterDone() {
			if (failed == 0 || obsoleteHashes.size() > 64)
				for (Hash hash : obsoleteHashesSnapshot)
					obsoleteHashes.remove(hash);

			done.onMessageChannelSubmitDone(succeeded, failed);
		}
	}

	public interface SubmitDone {
		void onMessageChannelSubmitCancelled();

		void onMessageChannelSubmitFailed(String reason);

		void onMessageChannelSubmitRecipientDone(@NonNull ActorOnStore recipient);

		void onMessageChannelSubmitRecipientFailed(@NonNull ActorOnStore recipient);

		void onMessageChannelSubmitDone(int succeeded, int failed);
	}
}
