package condensation.actorWithDataTree;

import java.util.HashMap;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actorGroups.ActorGroupBuilder;
import condensation.actors.ActorStatus;
import condensation.actors.GroupData;
import condensation.actors.MergeableData;
import condensation.actors.PublicKey;
import condensation.actors.PublicKeyCache;
import condensation.actors.Source;
import condensation.actors.messageBoxReader.ReceivedMessage;
import condensation.dataTree.BranchListener;
import condensation.dataTree.Selector;
import condensation.messaging.MessagingStore;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.Store;

public class ActorGroupFromSelector implements MergeableData, BranchListener {
	public final MessagingStore messagingStore;
	public final GroupData groupData;
	public final Selector actorGroupSelector;
	public final Selector actorSelector;
	public final Selector entrustedActorsSelector;
	public final Bytes label;
	public final Delegate delegate;

	final HashMap<Bytes, Member> members = new HashMap<>();
	final PublicKeyCache publicKeyCache = new PublicKeyCache(128);

	// Message state
	long revision = 0;
	Bytes version = Bytes.empty;
	Record currentMessage = new Record();

	public ActorGroupFromSelector(MessagingStore messagingStore, GroupData groupData, Selector actorGroupSelector, Selector entrustedActorsSelector, Bytes groupDataMessageLabel, Delegate delegate) {
		this.messagingStore = messagingStore;
		this.groupData = groupData;
		this.actorGroupSelector = actorGroupSelector;
		actorSelector = actorGroupSelector.child(messagingStore.actor.keyPair.publicKey.hash.bytes.slice(0, 16));
		this.entrustedActorsSelector = entrustedActorsSelector;
		this.label = groupDataMessageLabel;
		this.delegate = delegate;

		messagingStore.actor.privateRoot.addDataHandler(label, this);
		actorGroupSelector.trackBranch(this);
		entrustedActorsSelector.trackBranch(this);
	}

	@Override
	public void onBranchChanged(Iterable<Selector> selectors) {
		for (Member member : members.values())
			member.isUsed = false;

		Hash me = messagingStore.actor.keyPair.publicKey.hash;
		for (Selector child : actorGroupSelector.children()) {
			if (child.child(BC.revoked).booleanValue()) continue;
			if (!child.child(BC.group_data).booleanValue()) continue;

			Record record = child.record();
			Hash hash = record.child(BC.hash).hashValue();
			if (hash == null) continue;
			if (hash.equals(me)) continue;

			Bytes label = hash.bytes.concatenate(record.child(BC.store).bytesValue());
			Member member = members.get(label);
			if (member != null) {
				member.isUsed = true;
				continue;
			}

			String storeUrl = record.child(BC.store).textValue();
			Store store = delegate.onVerifyMemberStore(storeUrl, hash);
			if (store == null) continue;
			members.put(label, new Member(this, label, hash, store));
		}

		for (Member member : members.values()) {
			if (member.isUsed) continue;
			members.remove(member.label);
		}
	}

	public long shareGroupData() {
		// Create the group data message, and check if it changed
		Record message = groupData.createMessage();
		Hash versionHash = message.toObject().calculateHash();
		if (versionHash.bytes.equals(version)) return revision;

		revision = System.currentTimeMillis();
		version = versionHash.bytes;
		messagingStore.actor.privateRoot.dataChanged();

		for (Member member : members.values())
			member.schedule();

		return revision;
	}

	// *** Ourselves

	public void setMyName(String name) {
		actorSelector.child(BC.name).set(name);
	}

	public String getMyName() {
		return actorSelector.child(BC.name).textValue();
	}

	public void updateMyRegistration() {
		PublicKey publicKey = messagingStore.actor.keyPair.publicKey;
		actorSelector.addObject(publicKey.hash, publicKey.object);
		Record record = new Record();
		record.add(BC.hash).add(publicKey.hash);
		record.add(BC.store).add(messagingStore.storeUrl);
		actorSelector.set(record);
	}

	public void setMyActiveFlag(boolean flag) {
		actorSelector.child(BC.active).set(flag);
	}

	public void setMyGroupDataFlag(boolean flag) {
		actorSelector.child(BC.group_data).set(flag);
	}

	// *** Actor group

	// Returns true if the account belongs to us.
	public boolean isGroupMember(Hash actorHash) {
		if (actorHash.equals(messagingStore.actor.keyPair.publicKey.hash)) return true;
		Selector memberSelector = findMember(actorHash);
		if (memberSelector == null) return false;
		return !memberSelector.child(BC.revoked).isSet();
	}

	// Returns the selector of a known member (independent of its state).
	public Selector findMember(Hash memberHash) {
		for (Selector child : actorGroupSelector.children()) {
			if (!memberHash.bytes.slice(0, child.label.byteLength).equals(child.label)) continue;
			Record record = child.record();
			Hash hash = record.child(BC.hash).hashValue();
			if (hash == null) continue;
			if (!hash.equals(memberHash)) continue;
			return child;
		}

		return null;
	}

	public void forgetOldIdleActors(long limit) {
		for (Selector child : actorGroupSelector.children()) {
			if (child.child(BC.active).booleanValue()) continue;
			if (child.child(BC.group_data).booleanValue()) continue;
			if (child.revision() > limit) continue;
			child.forgetBranch();
		}
	}

	// *** Mergeable data interface

	@Override
	public void addDataTo(Record record) {
		if (this.revision == 0) return;
		record.add(this.revision).add(this.version);
	}

	@Override
	public void mergeData(Record record) {
		for (Record child : record.children) {
			long revision = child.asInteger();
			if (revision <= this.revision) continue;

			this.revision = revision;
			this.version = child.bytesValue();
		}
	}

	@Override
	public void mergeExternalData(Store store, Record record, Source source) {
		mergeData(record);
		if (source == null) return;
		source.keep();
		messagingStore.actor.privateRoot.unsaved.state.addMergedSource(source);
	}

	// *** Receiving messages

	public boolean processGroupDataMessage(ReceivedMessage message, Record section) {
		if (!isGroupMember(message.sender.publicKey.hash)) {
			// TODO: If the sender is not a known group member, we should run actor group discovery on the sender. He may be part of us, but we don't know that yet.
			return false;
		}

		groupData.mergeMessage(message, section);
		return true;
	}

	// *** Actor group for announcing

	public ActorGroupBuilder getActorGroupBuilder() {
		ActorGroupBuilder builder = new ActorGroupBuilder();

		Hash me = messagingStore.actor.keyPair.publicKey.hash;
		builder.addMember(me, messagingStore.storeUrl, System.currentTimeMillis(), ActorStatus.ACTIVE);
		for (Selector child : actorGroupSelector.children()) {
			Record record = child.record();
			Hash hash = record.child(BC.hash).hashValue();
			if (hash == null) continue;
			if (hash.equals(me)) continue;
			String storeUrl = record.child(BC.store).textValue();
			Selector revokedSelector = child.child(BC.revoked);
			Selector activeSelector = child.child(BC.active);
			long revision = Condensation.max3(child.revision(), revokedSelector.revision(), activeSelector.revision());
			ActorStatus actorStatus = revokedSelector.booleanValue() ? ActorStatus.REVOKED : activeSelector.booleanValue() ? ActorStatus.ACTIVE : ActorStatus.IDLE;
			builder.addMember(hash, storeUrl, revision, actorStatus);
		}

		for (Selector child : entrustedActorsSelector.children()) {
			Record record = child.record();
			Hash hash = record.child(BC.hash).hashValue();
			if (hash == null) continue;
			if (hash.equals(me)) continue;
			String storeUrl = record.child(BC.store).textValue();
			builder.addEntrustedActor(hash, storeUrl);
			builder.entrustedActorsRevision = Math.max(builder.entrustedActorsRevision, child.revision());
		}


		return builder;
	}

	// *** Delegate

	public interface Delegate {
		Store onVerifyMemberStore(@NonNull String storeUrl, @NonNull Hash actorHash);

		void onGroupDataShared(Member member, long revision);
	}
}
//*/