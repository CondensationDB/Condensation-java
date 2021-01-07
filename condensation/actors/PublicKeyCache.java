package condensation.actors;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import condensation.Condensation;
import condensation.serialization.Hash;

public class PublicKeyCache {
	public final int maxSize;
	final HashMap<Hash, Entry> cache = new HashMap<>();

	public PublicKeyCache(int maxSize) {
		this.maxSize = Math.max(1, maxSize);
	}

	public void add(@NonNull PublicKey publicKey) {
		cache.put(publicKey.hash, new Entry(publicKey));
		checkSize();
	}

	public PublicKey get(@NonNull Hash hash) {
		Entry entry = cache.get(hash);
		if (entry == null) return null;
		entry.lastAccess = System.currentTimeMillis();
		return entry.publicKey;
	}

	private void checkSize() {
		int count = cache.size();
		if (count < maxSize) return;

		Entry[] entries = new Entry[count];
		cache.values().toArray(entries);
		Arrays.sort(entries);
		for (int i = 0; i < count; i++) {
			cache.remove(entries[i].publicKey.hash);
		}
	}

	public void removeOldKeys(long validity) {
		long threshold = System.currentTimeMillis() - validity;
		ArrayList<Hash> toRemove = new ArrayList<>();
		for (Entry entry : cache.values())
			if (entry.lastAccess < threshold)
				toRemove.add(entry.publicKey.hash);

		for (Hash hash : toRemove)
			cache.remove(hash);
	}

	class Entry implements Comparable<Entry> {
		final PublicKey publicKey;
		long lastAccess;

		Entry(PublicKey publicKey) {
			this.publicKey = publicKey;
			lastAccess = System.currentTimeMillis();
		}

		@Override
		public int compareTo(@NonNull Entry that) {
			return Condensation.longCompare(lastAccess, that.lastAccess);
		}
	}
}
