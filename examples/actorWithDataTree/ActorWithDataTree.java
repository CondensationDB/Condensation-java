package condensation.examples.actorWithDataTree;

import android.content.Context;
import android.util.Log;

import java.io.File;

import androidx.annotation.NonNull;
import condensation.actorWithDataTree.ActorGroupFromSelector;
import condensation.actorWithDataTree.EntrustedKeysFromSelector;
import condensation.actorWithDataTree.Member;
import condensation.actorWithDataTree.RootDataTree;
import condensation.actors.Actor;
import condensation.actors.GroupData;
import condensation.actors.KeyPair;
import condensation.actors.PublicKeyCache;
import condensation.actors.messageBoxReader.MessageBoxReaderPool;
import condensation.dataTree.Selector;
import condensation.messaging.Announce;
import condensation.messaging.MessagingStore;
import condensation.serialization.Hash;
import condensation.stores.HashVerificationStore;
import condensation.stores.ObjectCache;
import condensation.stores.Store;
import condensation.stores.folder.FolderStore;
import condensation.stores.http.HTTPStore;

public class ActorWithDataTree extends Actor implements ActorGroupFromSelector.Delegate {
	// Cache store
	public final Store cacheStore;

	// Private data
	public final RootDataTree groupDataTree;
	public final RootDataTree localDataTree;
	public final Selector groupRoot;
	public final Selector localRoot;
	public final EntrustedKeysFromSelector entrustedKeys;

	// Message queue
	public final MessagingStore messagingStore;
	public final MessageProcessor messageProcessor;

	// Actor group
	public final ActorGroupFromSelector group;

	// State
	boolean privateDataReady = false;

	public ActorWithDataTree(@NonNull KeyPair keyPair, @NonNull Context context, @NonNull String messagingStoreUrl) {
		super(keyPair, createFolderStore(context.getFilesDir(), true));

		// Cache store
		cacheStore = createFolderStore(context.getCacheDir(), false);

		// Data trees
		groupDataTree = new RootDataTree(privateRoot, BC.group_data_tree);
		localDataTree = new RootDataTree(privateRoot, BC.local_data_tree);
		groupRoot = groupDataTree.root;
		localRoot = localDataTree.root;

		// Entrusted actors
		entrustedKeys = new EntrustedKeysFromSelector(this, groupRoot.child(condensation.actors.BC.entrusted_actors));

		// Prepare a messaging store with group data and a message reader
		messagingStore = new MessagingStore(this, store(messagingStoreUrl), messagingStoreUrl, entrustedKeys);
		MessageBoxReaderPool pool = new MessageBoxReaderPool(keyPair, new PublicKeyCache(128));
		messageProcessor = new MessageProcessor(this);

		// Group data sharing
		GroupData groupData = new GroupData();
		groupData.add(groupDataTree.label, groupDataTree);

		// Prepare the actor group
		group = new ActorGroupFromSelector(messagingStore, groupData, groupRoot.child(condensation.actors.BC.actor_group), groupRoot.child(condensation.actors.BC.entrusted_actors), BC.group_data, this);

		// Start by reading our private data
		new ProcurePrivateData(this);
	}

	public static FolderStore createFolderStore(File folder, boolean enforceCompleteness) {
		folder.mkdirs();
		FolderStore store = new FolderStore(folder, enforceCompleteness);
		store.createIfNecessary();
		return store;
	}

	public Store store(@NonNull String url) {
		HTTPStore store = HTTPStore.forUrl(url);
		if (store == null) return null;
		return new ObjectCache(new HashVerificationStore(store), cacheStore);
	}

	public void onProcurePrivateDataDone() {
		//messageBoxReader.read();

		Announce announce = new Announce(messagingStore);
		announce.card.add("name").add(groupRoot.child("name").bytesValue());
		announce.submit(new Announce.SubmitDone() {
			@Override
			public void onAnnounceDone() {
				Log.i("Actor", "Announced.");
			}

			@Override
			public void onAnnounceFailed() {
				Log.i("Actor", "Announcing failed.");
			}
		});
	}

	public void onProcurePrivateDataFailed() {
		Log.i("Actor", "Cannot read private data.");
	}

	void save() {
		new SavePrivateData(this);
	}

	void onSavingDone() {
		Log.i("Actor", "Private data saved.");
	}

	void onSavingFailed() {
		Log.i("Actor", "Saving the private data failed.");
	}

	@Override
	public Store onVerifyMemberStore(@NonNull String storeUrl, @NonNull Hash actorHash) {
		return store(storeUrl);
	}

	@Override
	public void onGroupDataShared(Member member, long revision) {
		Log.i("Actor", "Group data shared with " + member.hash.shortHex());
	}
}
