package condensation.stores.http;

import java.util.HashSet;

import condensation.Condensation;
import condensation.serialization.Hash;

public class HTTPStoreState {
	public final String storeUrl;
	public final RequestQueue getQueue = new RequestQueue(this);
	public final RequestQueue putSmallQueue = new RequestQueue(this);
	public final RequestQueue putLargeQueue = new RequestQueue(this);

	// This is used by HttpStoreManager to remove entries not in use any more
	long lastUsed = 0L;

	HTTPStoreState(String storeUrl) {
		this.storeUrl = storeUrl;
	}

	// *** Reachability estimate and statistics

	long lastReached = 0L;
	long lastNotReached = 0L;
	int requestsDone = 0;
	String lastError = null;

	public void updateReachability(String error) {
		Condensation.assertMainThread();
		requestsDone += 1;
		long now = System.currentTimeMillis();
		lastUsed = now;
		if (error == null) {
			lastReached = now;
		} else {
			lastNotReached = now;
			lastError = error;
		}
	}

	public int isReachable() {
		long now = System.currentTimeMillis();
		if (now - lastReached < 5 * Condensation.SECOND) return 1;
		if (lastReached > lastNotReached && lastReached > now - 120 * Condensation.SECOND) return 1;
		if (lastNotReached > lastReached && lastNotReached > now - 120 * Condensation.SECOND) return -1;
		return 0;
	}

	public boolean isDisabled() {
		if (lastReached > lastNotReached) return false;
		long now = System.currentTimeMillis();
		return lastNotReached > now - 1;
	}

	// *** Object booking

	// Objects we booked within the last 10 - 20 minutes
	HashSet<Hash> booked = new HashSet<>();
	long bookedAge = 0L;
	HashSet<Hash> previouslyBooked = new HashSet<>();
	long previouslyBookedAge = 0L;

	public boolean isBooked(Hash hash) {
		rotateBooked();
		return booked.contains(hash) || previouslyBooked.contains(hash);
	}

	public void setBooked(Hash hash) {
		rotateBooked();
		booked.add(hash);
	}

	void rotateBooked() {
		long now = System.currentTimeMillis();

		if (now - bookedAge > Condensation.MINUTE * 10) {
			previouslyBooked = booked;
			previouslyBookedAge = bookedAge;
			booked = new HashSet<>();
			bookedAge = now;
		}

		if (now - previouslyBookedAge > Condensation.MINUTE * 20) {
			previouslyBookedAge = bookedAge;
			previouslyBooked = new HashSet<>();
		}
	}
}
