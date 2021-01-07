package condensation.actorGroups;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

import condensation.Condensation;
import condensation.ImmutableList;
import condensation.actors.ActorOnStore;
import condensation.actors.ActorStatus;
import condensation.actors.PublicKey;
import condensation.serialization.Hash;

public class ActorGroup {
	public static final ImmutableList<Member> noMembers = new ImmutableList<>();
	public static final ImmutableList<EntrustedActor> noEntrustedKeys = new ImmutableList<>();

	// *** Object
	public final ImmutableList<Member> members;
	public final ImmutableList<EntrustedActor> entrustedActors;
	public final long entrustedActorsRevision;
	private final HashSet<Hash> containsCache = new HashSet<>();

	// Members must be sorted in descending revision order, such that the member with the most recent revision is first. Members must not include any revoked actors.
	public ActorGroup(ImmutableList<Member> members, long entrustedActorsRevision, ImmutableList<EntrustedActor> entrustedActors) {
		this.members = members;
		this.entrustedActorsRevision = entrustedActorsRevision;
		this.entrustedActors = entrustedActors;

		for (Member member : members)
			containsCache.add(member.actorOnStore.publicKey.hash);
	}

	@Override
	public String toString() {
		return members.length == 0 ? "ActorGroup:inactive" : "ActorGroup:" + members.get(0).actorOnStore.publicKey.hash.shortHex();
	}

	// Checks whether the actor group contains at least one active member.
	public boolean isActive() {
		for (Member member : members)
			if (member.isActive) return true;
		return false;
	}

	// Returns the most recent active member, the most recent idle member, or null if no such member exists.
	public Member leader() {
		for (Member member : members)
			if (member.isActive) return member;
		return members.length > 0 ? members.get(0) : null;
	}

	// Returns true if the account belongs to this actor group.
	// Note that multiple (different) actor groups may claim that the account belongs to them. In practice, an account usually belongs to one actor group.
	public boolean contains(Hash actorHash) {
		return containsCache.contains(actorHash);
	}

	// Returns true if the account is entrusted by this actor group.
	public boolean entrusts(Hash actorHash) {
		for (EntrustedActor actor : entrustedActors)
			if (actor.actorOnStore.publicKey.hash.equals(actorHash)) return true;
		return false;
	}

	// Returns all public keys.
	public ArrayList<PublicKey> publicKeys() {
		ArrayList<PublicKey> publicKeys = new ArrayList<>();
		for (Member member : members)
			publicKeys.add(member.actorOnStore.publicKey);
		for (EntrustedActor actor : entrustedActors)
			publicKeys.add(actor.actorOnStore.publicKey);
		return publicKeys;
	}

	// Returns an ActorGroupBuilder with all members and entrusted keys of this ActorGroup.
	public ActorGroupBuilder toBuilder() {
		ActorGroupBuilder builder = new ActorGroupBuilder();
		builder.entrustedActorsRevision = entrustedActorsRevision;
		for (Member member : members)
			builder.addMember(member.actorOnStore, member.storeUrl, member.revision, member.isActive ? ActorStatus.ACTIVE : ActorStatus.IDLE);
		if (builder.mergeEntrustedActors(entrustedActorsRevision))
			for (EntrustedActor actor : entrustedActors)
				builder.addEntrustedActor(actor.actorOnStore.publicKey, actor.storeUrl);
		return builder;
	}

	// Members

	public static class Member {
		public final ActorOnStore actorOnStore;
		public final String storeUrl;
		public final long revision;
		public final boolean isActive;

		public Member(ActorOnStore actorOnStore, String storeUrl, long revision, boolean isActive) {
			this.actorOnStore = actorOnStore;
			this.storeUrl = storeUrl;
			this.revision = revision;
			this.isActive = isActive;
		}
	}

	public static final Comparator<Member> byRevision = new Comparator<Member>() {
		@Override
		public int compare(Member a, Member b) {
			int cmp = Condensation.longCompare(b.revision, a.revision);
			if (cmp == 0) cmp = Condensation.booleanCompare(b.isActive, a.isActive);
			return cmp;
		}
	};

	// Entrusted actors

	public static class EntrustedActor {
		public final ActorOnStore actorOnStore;
		public final String storeUrl;

		public EntrustedActor(ActorOnStore actorOnStore, String storeUrl) {
			this.actorOnStore = actorOnStore;
			this.storeUrl = storeUrl;
		}
	}
}
