package condensation.examples.actorWithRecord;

import android.content.Context;
import android.util.Log;

import java.io.File;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.Actor;
import condensation.actors.EntrustedKeysList;
import condensation.actors.GroupData;
import condensation.actors.KeyPair;
import condensation.actors.PrivateRoot;
import condensation.actors.PublicKey;
import condensation.actors.Source;
import condensation.messaging.Announce;
import condensation.messaging.MessagingStore;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.stores.HashVerificationStore;
import condensation.stores.ObjectCache;
import condensation.stores.Store;
import condensation.stores.folder.FolderStore;
import condensation.stores.http.HTTPStore;

public class ActorWithRecord extends Actor implements PrivateRoot.ProcureDone, PrivateRoot.SavingDone {
	// Cache store
	public final Store cacheStore;

	// Entrusted keys
	final EntrustedKeysList entrustedKeys = new EntrustedKeysList();

	// Private data
	final MostRecentRecord privateData;

	// Messaging
	public final MessagingStore messagingStore;
	public final MessageProcessor messageProcessor;

	// Actor group
	final ActorGroupManager group;

	// State
	boolean privateDataReady = false;

	public ActorWithRecord(@NonNull KeyPair keyPair, @NonNull Context context, @NonNull String messagingStoreUrl) {
		super(keyPair, createFolderStore(context.getFilesDir(), true));

		// Cache store
		cacheStore = createFolderStore(context.getCacheDir(), false);

		// Private data
		privateData = new MostRecentRecord(privateRoot, BC.group_data);

		// Entrusted keys
		entrustedKeys.add(PublicKey.from(CondensationObject.from(Bytes.fromHex("00000000c16503010001416e1ee300a3820dc11aeee5527a2d3ff74e9227c172550e7ac4e0b9071eb1bee975e826c99a640b7da20d19afa21eebbde419382d781c4582034cb80437a7b5d61785e57f3f69eeaf64f447c38070dde89bf75cb39701fe1724c079c346a0bb57cecb6fbaaf5fec505f22cf07cc0a4d330d6839dc8c966826d552574724bc2f4fc22eb9963a0e8d4cc9f461646f8b08ebec472a2b449d99a5671910de1483ee9283d7abe0599309fd02fb6b4db15fe52c39c8777b34c4d43559c6d5e952c7c95893e3a7fe49458a21fd46607b53cf3bf18cd8e1d441bf7ae3aa57cf1192654789905805d675262ca67e91059ad9c65bb9a701603f879bf5c0234c84790956ee21c8963773"))));

		// Messaging store
		messagingStore = new MessagingStore(this, store(messagingStoreUrl), messagingStoreUrl, entrustedKeys);
		messageProcessor = new MessageProcessor(this);

		// Actor group
		GroupData groupData = new GroupData();
		groupData.add(Bytes.fromText("data"), privateData);
		group = new ActorGroupManager(this, messagingStore, groupData);

		privateRoot.procure(Condensation.DAY, this);
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

	// Loading the private data

	@Override
	public void onPrivateRootProcureDone() {
		privateDataReady = true;

		Announce announce = new Announce(messagingStore);
		announce.card.add("name").add(privateData.record.child(BC.name).bytesValue());
		announce.submit(new Announce.SubmitDone() {
			@Override
			public void onAnnounceDone() {
				Log.i("ActorWithRecord", "Private data saved");
			}

			@Override
			public void onAnnounceFailed() {
				Log.i("ActorWithRecord", "Private data saved");
			}
		});

		group.update(privateData.record);
		messageProcessor.read();
	}

	@Override
	public void onPrivateRootProcureInvalidEntry(@NonNull Source source, @NonNull String reason) {
		Log.i("ActorWithRecord", "Invalid entry " + source.toString() + ": " + reason);
	}

	@Override
	public void onPrivateRootProcureFailed() {
		Log.e("ActorWithRecord", "Private data could not be loaded");
	}

	// Saving the private data

	void save() {
		privateRoot.save(entrustedKeys, this);
		group.shareGroupData();
	}

	@Override
	public void onPrivateRootSavingDone() {
		Log.i("ActorWithRecord", "Private data saved");
	}

	@Override
	public void onPrivateRootSavingFailed() {
		Log.e("ActorWithRecord", "Private data could not be saved");
	}
}
