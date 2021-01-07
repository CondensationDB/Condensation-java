package condensation.actors;

import androidx.annotation.NonNull;

import condensation.serialization.Bytes;

public class ActorStatus implements Comparable<ActorStatus> {
	// *** Static ***
	public static final ActorStatus ACTIVE = new ActorStatus("active", 0);
	public static final ActorStatus IDLE = new ActorStatus("idle", 1);
	public static final ActorStatus REVOKED = new ActorStatus("revoked", 2);

	public static ActorStatus fromBytes(Bytes bytes) {
		if (bytes.equals(ACTIVE.asBytes)) return ACTIVE;
		if (bytes.equals(IDLE.asBytes)) return IDLE;
		if (bytes.equals(REVOKED.asBytes)) return REVOKED;
		return null;
	}

	public static ActorStatus fromText(String text) {
		if (text.equals(ACTIVE.asText)) return ACTIVE;
		if (text.equals(IDLE.asText)) return IDLE;
		if (text.equals(REVOKED.asText)) return REVOKED;
		return null;
	}

	// *** Object ***
	public final String asText;
	public final Bytes asBytes;
	public final int order;

	private ActorStatus(String label, int order) {
		asText = label;
		asBytes = Bytes.fromText(label);
		this.order = order;
	}

	@Override
	public String toString() {
		return asText;
	}

	@Override
	public int compareTo(@NonNull ActorStatus that) {
		return order - that.order;
	}
}
