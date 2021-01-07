package condensation.actorGroups;

import java.util.HashMap;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.ImmutableList;
import condensation.actors.ActorOnStore;
import condensation.actors.ActorStatus;
import condensation.actors.BC;
import condensation.actors.KeyPair;
import condensation.actors.PublicKey;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.Store;

public class ActorGroupBuilder {
	public final HashMap<Hash, PublicKey> knownPublicKeys = new HashMap<>();
	public final HashMap<String, Member> members = new HashMap<>();
	public final HashMap<Hash, EntrustedActor> entrustedActors = new HashMap<>();
	public long entrustedActorsRevision = 0;

	public void addKnownPublicKey(PublicKey publicKey) {
		knownPublicKeys.put(publicKey.hash, publicKey);
	}

	public boolean addMember(Hash hash, String storeUrl, long revision, ActorStatus status) {
		String url = storeUrl + "/accounts/" + hash.hex();
		Member member = members.get(url);
		if (member != null && revision <= member.revision) return false;
		members.put(url, new Member(hash, storeUrl, revision, status));
		return true;
	}

	public void removeMember(Hash hash, String storeUrl) {
		String url = storeUrl + "/accounts/" + hash.hex();
		members.remove(url);
	}

	public boolean addMember(ActorOnStore actorOnStore, String storeUrl, long revision, ActorStatus status) {
		addKnownPublicKey(actorOnStore.publicKey);
		return addMember(actorOnStore.publicKey.hash, storeUrl, revision, status);
	}

	public void parseMembers(Record record, boolean linkedPublicKeys) {
		for (Record storeRecord : record.children) {
			String storeUrl = storeRecord.asText();

			for (Record statusRecord : storeRecord.children) {
				ActorStatus status = ActorStatus.fromBytes(statusRecord.bytes);

				for (Record child : statusRecord.children) {
					Hash hash = linkedPublicKeys ? child.hash : Hash.from(child.bytes);
					if (hash == null) continue;
					addMember(hash, storeUrl, child.integerValue(), status);
				}
			}
		}
	}

	public boolean mergeEntrustedActors(long revision) {
		if (revision <= entrustedActorsRevision) return false;
		entrustedActorsRevision = revision;
		entrustedActors.clear();
		return true;
	}

	public void addEntrustedActor(Hash hash, String storeUrl) {
		EntrustedActor actor = new EntrustedActor(hash, storeUrl);
		entrustedActors.put(hash, actor);
	}

	public void removeEntrustedActor(Hash hash) {
		entrustedActors.remove(hash);
	}

	public void addEntrustedActor(PublicKey publicKey, String storeUrl) {
		addKnownPublicKey(publicKey);
		addEntrustedActor(publicKey.hash, storeUrl);
	}

	public void addEntrustedActor(condensation.actors.EntrustedActor entrustedActor) {
		addKnownPublicKey(entrustedActor.publicKey);
		addEntrustedActor(entrustedActor.publicKey.hash, entrustedActor.storeUrl);
	}

	public void parseEntrustedActors(Record record, boolean linkedPublicKeys) {
		for (Record revisionRecord : record.children)
			if (mergeEntrustedActors(revisionRecord.asInteger()))
				parseEntrustedActorList(revisionRecord, linkedPublicKeys);
	}

	public void parseEntrustedActorList(Record record, boolean linkedPublicKeys) {
		for (Record storeRecord : record.children) {
			String storeUrl = storeRecord.asText();

			for (Record child : storeRecord.children) {
				Hash hash = linkedPublicKeys ? child.hash : Hash.from(child.bytes);
				if (hash == null) continue;
				addEntrustedActor(hash, storeUrl);
			}
		}
	}

	public void parse(Record record, boolean linkedPublicKeys) {
		parseMembers(record.child(BC.actor_group), linkedPublicKeys);
		parseEntrustedActors(record.child(BC.entrusted_actors), linkedPublicKeys);
	}

	public void load(Store store, KeyPair keyPair, LoadActorGroup.Delegate delegate) {
		new LoadActorGroup(this, store, keyPair, delegate);
	}

	public void discover(KeyPair keyPair, DiscoverActorGroup.Delegate delegate) {
		new DiscoverActorGroup(this, keyPair, delegate);
	}

	public Record toRecord(boolean linkedPublicKeys) {
		Record record = new Record();
		addMembersToRecord(record, linkedPublicKeys);
		addEntrustedActorListToRecord(record, linkedPublicKeys);
		return record;
	}

	public void addToRecord(Record record, boolean linkedPublicKeys) {
		addMembersToRecord(record, linkedPublicKeys);
		addEntrustedActorListToRecord(record, linkedPublicKeys);
	}

	public void addMembersToRecord(Record record, boolean linkedPublicKeys) {
		if (members.isEmpty()) return;

		Record membersRecord = record.add(BC.actor_group);
		String currentStoreUrl = null;
		Record currentStoreRecord = null;
		ActorStatus currentStatus = null;
		Record currentStatusRecord = null;
		for (Member member : ImmutableList.sorted(members.values())) {
			if (currentStoreUrl == null || !currentStoreUrl.equals(member.storeUrl)) {
				currentStoreUrl = member.storeUrl;
				currentStoreRecord = membersRecord.add(member.storeUrl);
				currentStatus = null;
				currentStatusRecord = null;
			}

			if (currentStatusRecord == null || currentStatus != member.status) {
				currentStatus = member.status;
				currentStatusRecord = currentStoreRecord.add(member.status.asBytes);
			}

			Record hashRecord = linkedPublicKeys ? currentStatusRecord.add(member.hash) : currentStatusRecord.add(member.hash.bytes);
			hashRecord.add(member.revision);
		}
	}

	public void addEntrustedActorListToRecord(Record record, boolean linkedPublicKeys) {
		if (entrustedActorsRevision <= 0) return;

		Record entrustedActorsRecord = record.add(BC.entrusted_actors).add(entrustedActorsRevision);
		String currentStoreUrl = "";
		Record currentStoreUrlRecord = null;
		for (EntrustedActor actor : ImmutableList.sorted(entrustedActors.values())) {
			if (currentStoreUrlRecord == null || !actor.storeUrl.equals(currentStoreUrl)) {
				currentStoreUrl = actor.storeUrl;
				currentStoreUrlRecord = entrustedActorsRecord.add(currentStoreUrl);
			}

			if (linkedPublicKeys) currentStoreUrlRecord.add(actor.hash);
			else currentStoreUrlRecord.add(actor.hash.bytes);
		}
	}

	public static class Member implements Comparable<Member> {
		public final Hash hash;
		public final String storeUrl;
		public final long revision;
		public final ActorStatus status;

		public Member(Hash hash, String storeUrl, long revision, ActorStatus status) {
			this.hash = hash;
			this.storeUrl = storeUrl;
			this.revision = revision;
			this.status = status;
		}

		@Override
		public int compareTo(@NonNull Member that) {
			int cmp = this.storeUrl.compareTo(that.storeUrl);
			if (cmp == 0) cmp = this.status.compareTo(that.status);
			if (cmp == 0) cmp = this.hash.compareTo(that.hash);
			if (cmp == 0) cmp = Condensation.longCompare(this.revision, that.revision);
			return cmp;
		}
	}

	public static class EntrustedActor implements Comparable<EntrustedActor> {
		public final Hash hash;
		public final String storeUrl;

		public EntrustedActor(Hash hash, String storeUrl) {
			this.hash = hash;
			this.storeUrl = storeUrl;
		}

		@Override
		public int compareTo(@NonNull EntrustedActor that) {
			int cmp = storeUrl.compareTo(that.storeUrl);
			if (cmp == 0) cmp = hash.compareTo(that.hash);
			return cmp;
		}
	}
}
