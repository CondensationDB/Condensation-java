package condensation.examples.actorWithRecord;

import java.util.Collections;
import java.util.HashMap;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actorGroups.ActorGroupBuilder;
import condensation.actors.ActorOnStore;
import condensation.actors.GetPublicKey;
import condensation.actors.GroupData;
import condensation.actors.PublicKey;
import condensation.actors.PublicKeyCache;
import condensation.actors.messageBoxReader.ReceivedMessage;
import condensation.messaging.MessageChannel;
import condensation.messaging.MessagingStore;
import condensation.tasks.LongLazyActionWithExponentialBackoff;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.Store;

public class ActorGroupManager {
	final ActorWithRecord actor;
	final MessagingStore messagingStore;
	final GroupData groupData;
	final HashMap<Bytes, Member> members = new HashMap<>();
	final PublicKeyCache publicKeyCache = new PublicKeyCache(128);

	// State
	public ActorGroupBuilder actorGroupBuilder = new ActorGroupBuilder();
	Record currentMessage = new Record();

	public ActorGroupManager(ActorWithRecord actor, MessagingStore messagingStore, GroupData groupData) {
		this.actor = actor;
		this.messagingStore = messagingStore;
		this.groupData = groupData;
	}

	public void update(Record record) {
		actorGroupBuilder = new ActorGroupBuilder();
		actorGroupBuilder.parse(record, true);

		for (Member member : members.values())
			member.isUsed = false;

		for (ActorGroupBuilder.Member groupMember : actorGroupBuilder.members.values()) {
			Bytes label = groupMember.hash.bytes.concatenate(Bytes.fromText(groupMember.storeUrl));
			Member member = members.get(label);
			if (member != null) {
				member.isUsed = true;
				continue;
			}

			Store store = actor.store(groupMember.storeUrl);
			if (store == null) continue;
			members.put(label, new Member(label, groupMember.hash, store));
		}

		for (Member member : members.values()) {
			if (member.isUsed) continue;
			members.remove(member.label);
		}
	}

	public void shareGroupData() {
		currentMessage = groupData.createMessage();
		for (Member member : members.values())
			member.schedule();
	}

	class Member extends LongLazyActionWithExponentialBackoff {
		final Bytes label;
		final Hash hash;
		final Store store;
		final MessageChannel messageChannel;

		// State
		boolean isUsed = true;
		ActorOnStore actorOnStore = null;
		boolean hasInvalidPublicKey = false;

		Member(Bytes label, Hash hash, Store store) {
			super(100, 2, Condensation.DAY);
			this.label = label;
			this.hash = hash;
			this.store = store;

			messageChannel = new MessageChannel(messagingStore, condensation.actorWithDataTree.BC.group_data.concatenate(hash.bytes), Condensation.MONTH);
			schedule();
		}

		@Override
		protected void action(Action action) {
			new SubmitAction(action);
		}

		class SubmitAction implements MessageChannel.SubmitDone, GetPublicKey.Done {
			final Action action;

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

				PublicKey publicKey = publicKeyCache.get(hash);
				if (publicKey != null) {
					actorOnStore = new ActorOnStore(publicKey, store);
					sendMessage();
					return;
				}

				new GetPublicKey(hash, store, messagingStore.actor.keyPair, this);
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
				if (currentMessage.children.isEmpty()) {
					action.done();
					return;
				}

				messageChannel.setRecipients(Collections.singleton(actorOnStore), Collections.EMPTY_LIST);
				messageChannel.addTransfer(currentMessage.dependentHashes(), messageChannel.messagingStore.actor.privateRoot.unsaved, "group data message");
				messageChannel.submit(currentMessage, this);
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
				if (succeeded > 0)
					action.done();
				else
					action.failed();
			}

			void failed() {
				action.failed();
			}
		}
	}

	// Reception

	public boolean isGroupMember(Hash hash) {
		for (Member member : members.values())
			if (member.hash.equals(hash)) return true;
		return false;
	}

	public boolean processGroupDataMessage(ReceivedMessage message, Record section) {
		if (!isGroupMember(message.sender.publicKey.hash)) {
			// TODO: If the sender is not a known group member, we should run actor group discovery on the sender. He may be part of us, but we don't know that yet.
			return false;
		}

		groupData.mergeMessage(message, section);
		return true;
	}
}
