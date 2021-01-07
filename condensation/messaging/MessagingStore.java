package condensation.messaging;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.Actor;
import condensation.actors.EntrustedKeysProvider;
import condensation.actors.PrivateRoot;
import condensation.actors.Source;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.stores.BoxLabel;
import condensation.stores.Store;
import condensation.unionList.UnionList;

public class MessagingStore {
	public final Actor actor;
	public final Store store;
	public final String storeUrl;
	public final EntrustedKeysProvider entrustedKeysProvider;

	public final PrivateRoot privateRoot;
	public final SentList sentList;

	public MessagingStore(Actor actor, Store store, String storeUrl, EntrustedKeysProvider entrustedKeysProvider) {
		this.actor = actor;
		this.store = store;
		this.storeUrl = storeUrl;
		this.entrustedKeysProvider = entrustedKeysProvider;

		privateRoot = new PrivateRoot(actor.keyPair, store);
		sentList = new SentList(privateRoot);
	}

	// *** Sending messages

	public boolean sentListReady = false;
	public ProcureSentList runningProdureSentList = null;

	public void procureSentList(ProcureSentListDone done) {
		if (sentListReady) {
			done.onProcureSentListDone();
			return;
		}

		if (runningProdureSentList == null) runningProdureSentList = new ProcureSentList();
		runningProdureSentList.handlers.add(done);
	}

	class ProcureSentList implements PrivateRoot.ProcureDone, UnionList.ReadDone {
		public final ArrayList<ProcureSentListDone> handlers = new ArrayList<>();

		ProcureSentList() {
			privateRoot.procure(Condensation.DAY, this);
		}

		@Override
		public void onPrivateRootProcureDone() {
			sentList.read(this);
		}

		@Override
		public void onPrivateRootProcureInvalidEntry(@NonNull Source source, @NonNull String reason) {
			Condensation.log("Private box entry " + source.hash.hex() + " is invalid: " + reason);
		}

		@Override
		public void onPrivateRootProcureFailed() {
			failed();
		}

		@Override
		public void onUnionListReadDone() {
			sentListReady = true;
			for (ProcureSentListDone done : handlers)
				done.onProcureSentListDone();
			runningProdureSentList = null;
		}

		@Override
		public void onUnionListReadFailed() {
			failed();
		}

		void failed() {
			for (ProcureSentListDone done : handlers)
				done.onProcureSentListFailed();
			runningProdureSentList = null;
		}
	}

	public interface ProcureSentListDone {
		void onProcureSentListDone();

		void onProcureSentListFailed();
	}

	public MessageChannel openMessageChannel(Bytes label, long validity) {
		return new MessageChannel(this, label, validity);
	}

	public void sendMessages(final SendMessagesDone done) {
		if (!sentList.hasChanges()) {
			Condensation.mainThread.post(new Runnable() {
				@Override
				public void run() {
					done.onSendMessagesDone();
				}
			});
			return;
		}

		new SendMessages(done);
	}

	class SendMessages implements PrivateRoot.SavingDone, UnionList.SaveDone {
		final SendMessagesDone done;

		SendMessages(SendMessagesDone done) {
			this.done = done;
			sentList.save(this);
		}

		@Override
		public void onUnionListSaveDone() {
			privateRoot.save(entrustedKeysProvider, this);
		}

		@Override
		public void onUnionListSaveFailed() {
			done.onSendMessagesFailed();
		}

		@Override
		public void onPrivateRootSavingDone() {
			done.onSendMessagesDone();
		}

		@Override
		public void onPrivateRootSavingFailed() {
			done.onSendMessagesFailed();
		}
	}

	public interface SendMessagesDone {
		void onSendMessagesDone();

		void onSendMessagesFailed();
	}

	/*/ *** Actor group

	void addGroupMember(Hash hash, Store store, long revision, ActorStatus status) {
		GroupMember groupMember = groupMembers.get(hash);
		if (groupMember == null) groupMember = new GroupMember(this, hash);
		if (groupMember.revision >= revision) return;
		groupMember.revision = revision;
		groupMember.store = store;
		groupMember.status = status;
	}

	void addGroupMember(PublicKey publicKey, Store store, long revision, ActorStatus status) {
		addGroupMember(publicKey.hash, store, revision, status);
	}

	void removeGroupMember(Hash hash) {
		groupMembers.remove(hash);
	}

	// *** Group data sharing

	void shareGroupData(ShareGroupDataDone done) {
		new ShareGroupData(done);
	}

	class ShareGroupData implements ActorGroupProvider.Done {
		final ShareGroupDataDone done;

		ShareGroupData(ShareGroupDataDone done) {
			this.done = done;
			actorGroupProvider.updateActorGroup(this);
		}

		@Override
		public void onActorGroupReady() {
			for (GroupMember groupMember : groupMembers.values())
				groupMember.reschedule();
			done.onShareGroupDataDone();
		}

		@Override
		public void onActorGroupNotReady() {
			done.onShareGroupDataFailed();
		}
	}

	interface ShareGroupDataDone {

		void onShareGroupDataDone();

		void onShareGroupDataFailed();
	}

	// */

	// *** Announcing

	public boolean announced = false;
	public boolean checkingIfAnnounced = false;
	private ArrayList<CheckIfAnnouncedDone> waitingUntilAnnounced = new ArrayList<>();

	public void setAnnounced() {
		announced = true;
		for (CheckIfAnnouncedDone done : waitingUntilAnnounced)
			done.onCheckIfAnnouncedDone();
		waitingUntilAnnounced.clear();
	}

	public void checkIfAnnounced(CheckIfAnnouncedDone done) {
		if (announced) {
			done.onCheckIfAnnouncedDone();
			return;
		}

		waitingUntilAnnounced.add(done);
		if (checkingIfAnnounced) return;
		checkingIfAnnounced = true;
		new CheckIfAnnounced();
	}

	class CheckIfAnnounced implements Store.ListDone {
		CheckIfAnnounced() {
			store.list(actor.keyPair.publicKey.hash, BoxLabel.PUBLIC, 0, actor.keyPair, this);
		}

		@Override
		public void onListDone(ArrayList<Hash> hashes) {
			checkingIfAnnounced = false;
			if (hashes.size() > 0) setAnnounced();
			else notAnnounced();
		}

		@Override
		public void onListStoreError(@NonNull String error) {
			checkingIfAnnounced = false;
			notAnnounced();
		}

		void notAnnounced() {
			for (CheckIfAnnouncedDone done : waitingUntilAnnounced)
				done.onCheckIfAnnouncedFailed();
			waitingUntilAnnounced.clear();
		}
	}

	public interface CheckIfAnnouncedDone {
		void onCheckIfAnnouncedDone();

		void onCheckIfAnnouncedFailed();
	}
}
