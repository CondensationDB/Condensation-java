package condensation.actorGroups;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.annotation.NonNull;
import condensation.ImmutableList;
import condensation.actors.ActorOnStore;
import condensation.actors.ActorStatus;
import condensation.actors.GetPublicKey;
import condensation.actors.KeyPair;
import condensation.actors.PublicKey;
import condensation.serialization.Hash;
import condensation.stores.Store;
import condensation.tasks.AwaitCounter;

public class LoadActorGroup implements AwaitCounter.Done {
	final KeyPair keyPair;
	final Store store;
	final Delegate delegate;
	final HashMap<Hash, PublicKey> knownPublicKeys;
	final long entrustedActorsRevision;
	final ArrayList<ActorGroup.Member> members = new ArrayList<>();
	final ArrayList<ActorGroup.EntrustedActor> entrustedActors = new ArrayList<>();
	final AwaitCounter awaitCounter = new AwaitCounter();

	// State
	boolean hasError = false;

	LoadActorGroup(ActorGroupBuilder builder, Store store, KeyPair keyPair, Delegate delegate) {
		this.knownPublicKeys = new HashMap<>(builder.knownPublicKeys);
		entrustedActorsRevision = builder.entrustedActorsRevision;
		this.store = store;
		this.keyPair = keyPair;
		this.delegate = delegate;

		for (ActorGroupBuilder.Member member : builder.members.values())
			addMember(member);
		for (ActorGroupBuilder.EntrustedActor actor : builder.entrustedActors.values())
			addEntrustedActor(actor);
		awaitCounter.then(this);
	}

	private void addMember(ActorGroupBuilder.Member member) {
		boolean isActive = member.status == ActorStatus.ACTIVE;
		boolean isIdle = member.status == ActorStatus.IDLE;
		if (!isActive && !isIdle) return;

		Store store = delegate.onLoadActorGroupVerifyStore(member.storeUrl);
		if (store == null) return;

		PublicKey publicKey = knownPublicKeys.get(member.hash);
		if (publicKey != null) {
			members.add(new ActorGroup.Member(new ActorOnStore(publicKey, store), member.storeUrl, member.revision, isActive));
			return;
		}

		new LoadKey(member, store, isActive);
	}

	private void addEntrustedActor(ActorGroupBuilder.EntrustedActor actor) {
		Store store = delegate.onLoadActorGroupVerifyStore(actor.storeUrl);
		if (store == null) return;

		PublicKey publicKey = knownPublicKeys.get(actor.hash);
		if (publicKey != null) {
			entrustedActors.add(new ActorGroup.EntrustedActor(new ActorOnStore(publicKey, store), actor.storeUrl));
			return;
		}

		new LoadEntrustedKey(actor, store);
	}

	class LoadKey implements GetPublicKey.Done {
		final ActorGroupBuilder.Member member;
		final Store accountStore;
		final boolean isActive;

		public LoadKey(ActorGroupBuilder.Member member, Store accountStore, boolean isActive) {
			this.member = member;
			this.accountStore = accountStore;
			this.isActive = isActive;

			awaitCounter.await();
			new GetPublicKey(member.hash, store, keyPair, this);
		}

		@Override
		public void onGetPublicKeyDone(@NonNull PublicKey publicKey) {
			knownPublicKeys.put(publicKey.hash, publicKey);
			members.add(new ActorGroup.Member(new ActorOnStore(publicKey, accountStore), member.storeUrl, member.revision, isActive));
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

	class LoadEntrustedKey implements GetPublicKey.Done {
		final ActorGroupBuilder.EntrustedActor actor;
		final Store accountStore;

		LoadEntrustedKey(ActorGroupBuilder.EntrustedActor actor, Store accountStore) {
			this.actor = actor;
			this.accountStore = accountStore;

			awaitCounter.await();
			new GetPublicKey(actor.hash, store, keyPair, this);
		}

		@Override
		public void onGetPublicKeyDone(@NonNull PublicKey publicKey) {
			knownPublicKeys.put(publicKey.hash, publicKey);
			entrustedActors.add(new ActorGroup.EntrustedActor(new ActorOnStore(publicKey, accountStore), actor.storeUrl));
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
		if (hasError) delegate.onLoadActorGroupFailed();
		delegate.onLoadActorGroupDone(new ActorGroup(ImmutableList.sorted(members), entrustedActorsRevision, ImmutableList.sorted(entrustedActors)));
	}

	public interface Delegate {
		Store onLoadActorGroupVerifyStore(String storeUrl);

		void onLoadActorGroupDone(ActorGroup actorGroup);

		void onLoadActorGroupFailed();
	}
}
