package condensation.actorGroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.ImmutableList;
import condensation.ImmutableStack;
import condensation.actors.ActorOnStore;
import condensation.actors.ActorStatus;
import condensation.actors.BC;
import condensation.actors.GetPublicKey;
import condensation.actors.KeyPair;
import condensation.actors.PublicKey;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.BoxLabel;
import condensation.stores.Store;
import condensation.tasks.RateLimitedTaskQueue;

public class DiscoverActorGroup {
	final HashMap<Hash, PublicKey> knownPublicKeys;
	final KeyPair keyPair;
	final Delegate delegate;

	// State
	final RateLimitedTaskQueue taskQueue = new RateLimitedTaskQueue(4);
	final HashMap<String, Node> nodesByUrl = new HashMap<>();
	final HashSet<Hash> coverage = new HashSet<>();

	DiscoverActorGroup(ActorGroupBuilder builder, KeyPair keyPair, Delegate delegate) {
		this.knownPublicKeys = new HashMap<>(builder.knownPublicKeys);
		this.keyPair = keyPair;
		this.delegate = delegate;

		for (ActorGroupBuilder.Member member : builder.members.values())
			addMember(member);

		for (Node node : nodesByUrl.values())
			taskQueue.enqueue(node);
		taskQueue.then(new TasksDone());
	}

	private void addMember(ActorGroupBuilder.Member member) {
		if (member.status != ActorStatus.ACTIVE) return;
		Node node = node(member.hash, member.storeUrl);
		node.merge(member.revision, ActorStatus.ACTIVE);
		coverage.add(member.hash);
	}

	private Node node(Hash actorHash, String storeUrl) {
		String url = storeUrl + "/" + actorHash.hex();
		Node node = nodesByUrl.get(url);
		if (node != null) return node;
		Node newNode = new Node(actorHash, storeUrl);
		nodesByUrl.put(url, newNode);
		return newNode;
	}

	private boolean covers(Hash hash) {
		return coverage.contains(hash);
	}

	private void extend() {
		// Start with the newest node
		Node mainNode = null;
		long mainRevision = -1;
		for (Node node : nodesByUrl.values()) {
			if (!node.attachedToUs) continue;
			if (node.revision <= mainRevision) continue;
			mainNode = node;
			mainRevision = node.revision;
		}

		if (mainNode == null) return;

		// Reset the reachable flag
		for (Node node : nodesByUrl.values())
			node.reachable = false;
		mainNode.reachable = true;

		// Traverse the graph along active links to find accounts to discover.
		ImmutableStack<Node> toCheck = new ImmutableStack<>(mainNode);
		while (toCheck.length > 0) {
			Node currentNode = toCheck.head;
			toCheck = toCheck.tail;
			for (Link link : currentNode.links) {
				Node node = link.node;
				if (node.reachable) continue;
				ActorStatus prospectiveStatus = link.revision > node.revision ? link.status : node.status;
				if (prospectiveStatus != ActorStatus.ACTIVE) continue;
				node.reachable = true;
				if (node.attachedToUs) toCheck = toCheck.with(node);
				else taskQueue.enqueue(node);
			}
		}
	}

	class Node implements Runnable, GetPublicKey.Done, Store.ListDone, Comparable<Node> {
		final String storeUrl;
		final Hash actorHash;

		// Newest status
		long revision = -1L;
		ActorStatus status = ActorStatus.IDLE;

		// Whether this node is reachable from the main node
		boolean reachable = false;

		// Store and public key
		Store store;
		ActorOnStore actorOnStore = null;

		// Links to other accounts
		final ArrayList<Link> links = new ArrayList<>();
		boolean attachedToUs = false;

		// Cards
		boolean cardsRead = false;
		final ArrayList<Card> cards = new ArrayList<>();
		final RateLimitedTaskQueue cardsTaskQueue = new RateLimitedTaskQueue(1);

		Node(Hash actorHash, String storeUrl) {
			this.storeUrl = storeUrl;
			this.actorHash = actorHash;
		}

		boolean isActive() {
			return status == ActorStatus.ACTIVE;
		}

		boolean isActiveOrIdle() {
			return status == ActorStatus.ACTIVE || status == ActorStatus.IDLE;
		}

		@Override
		public void run() {
			if (cardsRead) {
				attach();
				taskQueue.done();
				return;
			}

			cardsRead = true;

			// Get the store
			store = delegate.onDiscoverActorGroupVerifyStore(storeUrl);
			if (store == null) {
				// Invalid store, give up
				taskQueue.done();
				return;
			}

			// Get the public key if necessary
			if (actorOnStore == null) {
				PublicKey publicKey = knownPublicKeys.get(actorHash);
				if (publicKey == null) {
					// Retrieve the public key
					new GetPublicKey(actorHash, store, keyPair, this);
					return;
				}

				actorOnStore = new ActorOnStore(publicKey, store);
			}

			// List the public box
			list();
		}

		@Override
		public void onGetPublicKeyDone(@NonNull PublicKey publicKey) {
			actorOnStore = new ActorOnStore(publicKey, store);
			list();
		}

		@Override
		public void onGetPublicKeyInvalid(@NonNull String reason) {
			// Public key not found, or invalid, give up
			delegate.onDiscoverActorGroupInvalidPublicKey(store, actorHash, reason);
			taskQueue.done();
		}

		@Override
		public void onGetPublicKeyStoreError(@NonNull String error) {
			delegate.onDiscoverActorGroupStoreError(store, error);
			taskQueue.done();
		}

		void list() {
			store.list(actorHash, BoxLabel.PUBLIC, 0L, keyPair, this);
		}

		@Override
		public void onListDone(ArrayList<Hash> hashes) {
			for (Hash hash : hashes)
				cardsTaskQueue.enqueue(new ProcessCard(hash));
			cardsTaskQueue.then(allCardsRead);
		}

		@Override
		public void onListStoreError(@NonNull String error) {
			delegate.onDiscoverActorGroupStoreError(store, error);
			taskQueue.done();
		}

		final Runnable allCardsRead = new Runnable() {
			@Override
			public void run() {
				attach();
				taskQueue.done();
			}
		};

		@Override
		public int compareTo(@NonNull Node that) {
			return Condensation.longCompare(that.revision, revision);
		}

		class ProcessCard implements Runnable, Store.GetDone {
			final Hash envelopeHash;
			Record envelope = null;
			Hash cardHash = null;

			ProcessCard(Hash envelopeHash) {
				this.envelopeHash = envelopeHash;
			}

			@Override
			public void run() {
				// Open the envelope
				store.get(envelopeHash, keyPair, this);
			}

			@Override
			public void onGetDone(@NonNull CondensationObject object) {
				envelope = Record.from(object);
				if (envelope == null) {
					invalid("Envelope is not a record.");
					return;
				}

				cardHash = envelope.child(BC.content).hashValue();
				if (cardHash == null) {
					invalid("Missing content hash.");
					return;
				}

				if (!Condensation.verifyEnvelopeSignature(envelope, actorOnStore.publicKey, cardHash)) {
					invalid("Invalid signature.");
					return;
				}

				// Read the card
				store.get(cardHash, keyPair, new ProcessContent());
			}

			@Override
			public void onGetNotFound() {
				invalid("Envelope object not found.");
			}

			@Override
			public void onGetStoreError(@NonNull String error) {
				failed(error);
			}

			class ProcessContent implements Store.GetDone {
				@Override
				public void onGetDone(@NonNull CondensationObject object) {
					// Merge, and check again if necessary
					Record card = Record.from(object);
					if (card == null) {
						invalid("Card is not a record.");
						cardsTaskQueue.done();
						return;
					}

					// Add the card to the list of cards
					cards.add(new Card(storeUrl, actorOnStore, envelopeHash, envelope, cardHash, card));

					// Parse the account list
					ActorGroupBuilder builder = new ActorGroupBuilder();
					builder.parseMembers(card.child(BC.actor_group), false);
					for (ActorGroupBuilder.Member member : builder.members.values())
						links.add(new Link(node(member.hash, member.storeUrl), member.revision, member.status));

					cardsTaskQueue.done();
				}

				@Override
				public void onGetNotFound() {
					invalid("Card object not found.");
				}

				@Override
				public void onGetStoreError(@NonNull String error) {
					failed(error);
				}
			}

			void invalid(String reason) {
				delegate.onDiscoverActorGroupInvalidCard(actorOnStore, envelopeHash, reason);
				cardsTaskQueue.done();
			}

			void failed(String error) {
				delegate.onDiscoverActorGroupStoreError(store, error);
				cardsTaskQueue.done();
			}
		}

		void attach() {
			if (attachedToUs) return;
			if (!hasLinkToUs()) return;

			// Attach this node
			attachedToUs = true;

			// Merge all links
			for (Link link : links)
				link.node.merge(link.revision, link.status);

			// Add the hash to the coverage
			coverage.add(actorHash);

			// Try to extend further
			extend();
		}

		public void merge(long revision, ActorStatus status) {
			if (this.revision >= revision) return;
			this.revision = revision;
			this.status = status;
		}

		boolean hasLinkToUs() {
			if (covers(actorHash)) return true;
			for (Link link : links) {
				if (covers(link.node.actorHash)) return true;
			}
			return false;
		}

	}

	class Link {
		final Node node;
		final long revision;
		final ActorStatus status;

		Link(Node node, long revision, ActorStatus status) {
			this.node = node;
			this.revision = revision;
			this.status = status;
		}
	}

	public static class Card {
		public final String storeUrl;
		public final ActorOnStore actorOnStore;
		public final Hash envelopeHash;
		public final Record envelope;
		public final Hash cardHash;
		public final Record card;

		Card(String storeUrl, ActorOnStore actorOnStore, Hash envelopeHash, Record envelope, Hash cardHash, Record card) {
			this.storeUrl = storeUrl;
			this.actorOnStore = actorOnStore;
			this.envelopeHash = envelopeHash;
			this.envelope = envelope;
			this.cardHash = cardHash;
			this.card = card;
		}
	}

	class TasksDone implements Runnable {
		final ArrayList<ActorGroup.Member> members = new ArrayList<>();
		long entrustedActorsRevision = 0;
		final ArrayList<ActorGroup.EntrustedActor> entrustedActors = new ArrayList<>();
		final ArrayList<Card> cards = new ArrayList<>();

		Iterator<ActorGroupBuilder.EntrustedActor> entrustedActorsIterator;
		Store store = null;

		@Override
		public void run() {
			// Compile the list of actors and cards
			for (Node node : nodesByUrl.values()) {
				if (!node.reachable) continue;
				if (!node.attachedToUs) continue;
				if (node.actorOnStore == null) continue;
				if (!node.isActiveOrIdle()) continue;
				members.add(new ActorGroup.Member(node.actorOnStore, node.storeUrl, node.revision, node.isActive()));
				cards.addAll(node.cards);
			}

			// Get the newest list of entrusted accounts
			ActorGroupBuilder parser = new ActorGroupBuilder();
			for (Card card : cards)
				parser.parseEntrustedActors(card.card.child(BC.entrusted_actors), false);

			// Get the entrusted keys
			entrustedActorsRevision = parser.entrustedActorsRevision;
			entrustedActorsIterator = parser.entrustedActors.values().iterator();
			loadNextEntrustedActor();
		}

		void loadNextEntrustedActor() {
			while (entrustedActorsIterator.hasNext()) {
				ActorGroupBuilder.EntrustedActor actor = entrustedActorsIterator.next();

				store = delegate.onDiscoverActorGroupVerifyStore(actor.storeUrl);
				if (store == null) continue;

				PublicKey publicKey = knownPublicKeys.get(actor.hash);
				if (publicKey != null) {
					entrustedActors.add(new ActorGroup.EntrustedActor(new ActorOnStore(publicKey, store), actor.storeUrl));
					continue;
				}

				new LoadEntrustedActor(actor, store);
				return;
			}

			delegate.onDiscoverActorGroupDone(new ActorGroup(ImmutableList.sorted(members, ActorGroup.byRevision), entrustedActorsRevision, ImmutableList.from(entrustedActors)), cards);
		}

		class LoadEntrustedActor implements GetPublicKey.Done {
			final ActorGroupBuilder.EntrustedActor actor;
			final Store store;

			LoadEntrustedActor(ActorGroupBuilder.EntrustedActor actor, Store store) {
				this.actor = actor;
				this.store = store;
				new GetPublicKey(actor.hash, store, keyPair, this);
			}

			@Override
			public void onGetPublicKeyDone(@NonNull PublicKey publicKey) {
				entrustedActors.add(new ActorGroup.EntrustedActor(new ActorOnStore(publicKey, store), actor.storeUrl));
				loadNextEntrustedActor();
			}

			@Override
			public void onGetPublicKeyInvalid(@NonNull String reason) {
				delegate.onDiscoverActorGroupInvalidPublicKey(store, actor.hash, reason);
				loadNextEntrustedActor();
			}

			@Override
			public void onGetPublicKeyStoreError(@NonNull String error) {
				delegate.onDiscoverActorGroupStoreError(store, error);
				loadNextEntrustedActor();
			}
		}
	}

	public interface Delegate {
		Store onDiscoverActorGroupVerifyStore(String storeUrl);

		void onDiscoverActorGroupDone(ActorGroup actorGroup, ArrayList<Card> cards);

		void onDiscoverActorGroupInvalidCard(ActorOnStore actorOnStore, Hash envelopeHash, String reason);

		void onDiscoverActorGroupInvalidPublicKey(Store store, Hash actorHash, String reason);

		void onDiscoverActorGroupStoreError(Store store, String error);
	}
}
