package condensation.actorWithDataTree;

import java.util.Collections;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.ActorOnStore;
import condensation.actors.GetPublicKey;
import condensation.actors.PublicKey;
import condensation.messaging.MessageChannel;
import condensation.tasks.LongLazyActionWithExponentialBackoff;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.stores.Store;

public class Member extends LongLazyActionWithExponentialBackoff {
	public final ActorGroupFromSelector actorGroup;
	public final Bytes label;
	public final Hash hash;
	public final Store store;
	public final MessageChannel messageChannel;

	// State
	boolean isUsed = true;
	ActorOnStore actorOnStore = null;
	boolean hasInvalidPublicKey = false;

	Member(ActorGroupFromSelector actorGroup, Bytes label, Hash hash, Store store) {
		super(100, 2, Condensation.DAY);
		this.actorGroup = actorGroup;
		this.label = label;
		this.hash = hash;
		this.store = store;
		messageChannel = new MessageChannel(actorGroup.messagingStore, condensation.actorWithDataTree.BC.group_data.concatenate(label), Condensation.MONTH);
		schedule();
	}

	// *** Sending messages

	@Override
	protected void action(Action action) {
		new SubmitAction(action);
	}

	class SubmitAction implements MessageChannel.SubmitDone, GetPublicKey.Done {
		final Action action;

		// State
		long revision = 0;

		SubmitAction(Action action) {
			this.action = action;

			if (hasInvalidPublicKey) {
				action.done();
				return;
			}

			if (actorOnStore != null) {
				sendMessage();
				return;
			}

			PublicKey publicKey = actorGroup.publicKeyCache.get(hash);
			if (publicKey != null) {
				actorOnStore = new ActorOnStore(publicKey, store);
				sendMessage();
				return;
			}

			new GetPublicKey(hash, store, actorGroup.messagingStore.actor.keyPair, this);
		}

		@Override
		public void onGetPublicKeyDone(@NonNull PublicKey publicKey) {
			sendMessage();
		}

		@Override
		public void onGetPublicKeyInvalid(@NonNull String reason) {
			hasInvalidPublicKey = true;
			action.done();
		}

		@Override
		public void onGetPublicKeyStoreError(@NonNull String error) {
			failed();
		}

		void sendMessage() {
			if (actorGroup.currentMessage.children.isEmpty()) {
				action.done();
				return;
			}

			revision = actorGroup.revision;
			messageChannel.setRecipients(Collections.singleton(actorOnStore), Collections.EMPTY_LIST);
			messageChannel.addTransfer(actorGroup.currentMessage.dependentHashes(), messageChannel.messagingStore.actor.privateRoot.unsaved, "group data message");
			messageChannel.submit(actorGroup.currentMessage, this);
		}

		@Override
		public void onMessageChannelSubmitCancelled() {
			failed();
		}

		@Override
		public void onMessageChannelSubmitFailed(String reason) {
			failed();
		}

		@Override
		public void onMessageChannelSubmitRecipientDone(@NonNull ActorOnStore recipient) {
		}

		@Override
		public void onMessageChannelSubmitRecipientFailed(@NonNull ActorOnStore recipient) {
		}

		@Override
		public void onMessageChannelSubmitDone(int succeeded, int failed) {
			if (succeeded > 0) {
				action.done();
				actorGroup.delegate.onGroupDataShared(Member.this, revision);
			} else {
				action.failed();
			}
		}

		void failed() {
			action.failed();
		}
	}
}