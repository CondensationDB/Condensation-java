package condensation.stores;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import condensation.Condensation;
import condensation.actors.KeyPair;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;

public class InMemoryStore extends Store {
	public static InMemoryStore create() {
		return new InMemoryStore("inMemoryStore:" + Condensation.randomBytes(16).asHex());
	}

	public InMemoryStore(String id) {
		super(id);
	}

	final HashMap<Hash, ObjectEntry> objects = new HashMap<>();

	static class ObjectEntry {
		public final CondensationObject object;
		long booked = System.currentTimeMillis();
		boolean inUse = false;

		ObjectEntry(CondensationObject object) {
			this.object = object;
		}
	}

	final HashMap<Hash, Account> accounts = new HashMap<>();

	static class Account {
		final HashSet<Hash> messageBox = new HashSet<>();
		final HashSet<Hash> privateBox = new HashSet<>();
		final HashSet<Hash> publicBox = new HashSet<>();

		HashSet<Hash> box(BoxLabel label) {
			if (label == BoxLabel.MESSAGES) return messageBox;
			if (label == BoxLabel.PRIVATE) return privateBox;
			if (label == BoxLabel.PUBLIC) return publicBox;
			return null;
		}

		boolean isEmpty() {
			return messageBox.isEmpty() && privateBox.isEmpty() && publicBox.isEmpty();
		}
	}

	Account accountForWriting(Hash hash) {
		Account account = accounts.get(hash);
		if (account != null) return account;
		Account newAccount = new Account();
		accounts.put(hash, newAccount);
		return newAccount;
	}

	// Store interface

	@Override
	public void get(@NonNull final Hash hash, @NonNull KeyPair keyPair, @NonNull final GetDone done) {
		Condensation.mainThread.post(new Runnable() {
			@Override
			public void run() {
				ObjectEntry entry = objects.get(hash);
				if (entry == null) done.onGetNotFound();
				else done.onGetDone(entry.object);
			}
		});
	}

	@Override
	public void book(@NonNull final Hash hash, @NonNull KeyPair keyPair, @NonNull final BookDone done) {
		Condensation.mainThread.post(new Runnable() {
			@Override
			public void run() {
				if (book(hash)) done.onBookDone();
				else done.onBookNotFound();
			}
		});
	}

	@Override
	public void put(@NonNull final Hash hash, @NonNull final CondensationObject object, @NonNull KeyPair keyPair, @NonNull final PutDone done) {
		Condensation.mainThread.post(new Runnable() {
			@Override
			public void run() {
				put(hash, object);
				done.onPutDone();
			}
		});
	}

	@Override
	public void list(@NonNull final Hash accountHash, @NonNull final BoxLabel boxLabel, long timeout, @NonNull KeyPair keyPair, @NonNull final ListDone done) {
		Condensation.mainThread.post(new Runnable() {
			@Override
			public void run() {
				ArrayList<Hash> list = list(accountHash, boxLabel);
				if (list == null) done.onListStoreError("Invalid box label.");
				else done.onListDone(list);
			}
		});
	}

	@Override
	public void modify(@NonNull final Collection<BoxAddition> additions, @NonNull final Collection<BoxRemoval> removals, @NonNull KeyPair keyPair, @NonNull final ModifyDone done) {
		Condensation.mainThread.post(new Runnable() {
			@Override
			public void run() {
				for (BoxAddition addition : additions) {
					if (addition.object != null)
						put(addition.hash, addition.object);

					if (add(addition.accountHash, addition.boxLabel, addition.hash)) continue;
					done.onModifyStoreError("Invalid box label.");
					return;
				}

				for (BoxRemoval removal : removals)
					remove(removal.accountHash, removal.boxLabel, removal.hash);

				done.onModifyDone();
			}
		});
	}

	// Synchronous interface

	public CondensationObject get(Hash hash) {
		ObjectEntry entry = objects.get(hash);
		if (entry == null) return null;
		return entry.object;
	}

	public void put(Hash hash, CondensationObject object) {
		objects.put(hash, new ObjectEntry(object));
	}

	public boolean book(Hash hash) {
		ObjectEntry entry = objects.get(hash);
		if (entry == null) return false;
		entry.booked = System.currentTimeMillis();
		return true;
	}

	public ArrayList<Hash> list(Hash accountHash, BoxLabel boxLabel) {
		Account account = accounts.get(accountHash);
		if (account == null) return new ArrayList<>();

		HashSet<Hash> box = account.box(boxLabel);
		if (box == null) return null;
		return new ArrayList<>(box);
	}

	public boolean add(Hash accountHash, BoxLabel boxLabel, Hash hash) {
		HashSet<Hash> box = accountForWriting(accountHash).box(boxLabel);
		if (box == null) return false;
		box.add(hash);
		return true;
	}

	public void remove(Hash accountHash, BoxLabel boxLabel, Hash hash) {
		HashSet<Hash> box = accountForWriting(accountHash).box(boxLabel);
		if (box == null) return;
		box.remove(hash);
	}

	// Garbage collection

	public void collectGarbage(long graceTime) {
		// Mark all objects as not used
		for (ObjectEntry entry : objects.values()) entry.inUse = false;

		// Mark all objects newer than the grace time
		for (ObjectEntry entry : objects.values())
			if (entry.booked > graceTime) mark(entry);

		// Mark all objects referenced from a box
		for (Account account : accounts.values()) {
			for (Hash hash : account.messageBox) mark(hash);
			for (Hash hash : account.privateBox) mark(hash);
			for (Hash hash : account.publicBox) mark(hash);
		}

		// Remove empty accounts
		for (Iterator<Map.Entry<Hash, Account>> it = accounts.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Hash, Account> entry = it.next();
			if (entry.getValue().isEmpty()) it.remove();
		}

		// Remove obsolete objects
		for (Iterator<Map.Entry<Hash, ObjectEntry>> it = objects.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Hash, ObjectEntry> entry = it.next();
			if (!entry.getValue().inUse) it.remove();
		}
	}

	private void mark(Hash hash) {
		ObjectEntry child = objects.get(hash);
		if (child == null) return;
		mark(child);
	}

	private void mark(ObjectEntry entry) {
		if (entry.inUse) return;
		entry.inUse = true;

		// Mark all children
		for (Hash hash : entry.object.hashes()) mark(hash);
	}
}
