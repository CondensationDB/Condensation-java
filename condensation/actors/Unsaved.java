package condensation.actors;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import condensation.Condensation;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.stores.BoxAddition;
import condensation.stores.BoxLabel;
import condensation.stores.BoxRemoval;
import condensation.stores.Store;

public class Unsaved extends Store {
	public final Store store;

	public State state = new State();

	public static class State {
		public final HashMap<Hash, CondensationObject> objects = new HashMap<>();
		public final ArrayList<Source> mergedSources = new ArrayList<>();
		public final ArrayList<DataSavedHandler> dataSavedHandlers = new ArrayList<>();

		public void addObject(Hash hash, CondensationObject object) {
			objects.put(hash, object);
		}

		public void addMergedSource(Source source) {
			mergedSources.add(source);
		}

		public void addDataSavedHandler(DataSavedHandler handler) {
			dataSavedHandlers.add(handler);
		}

		public void merge(@NonNull State state) {
			objects.putAll(state.objects);
			mergedSources.addAll(state.mergedSources);
			dataSavedHandlers.addAll(state.dataSavedHandlers);
		}
	}

	public Unsaved(Store store) {
		super("Unsaved\n" + Condensation.randomBytes(16).asHex() + "\n" + store.id);
		this.store = store;
	}

	// *** Saving

	public State savingState = null;

	public boolean isSaving() {
		return savingState != null;
	}

	public void startSaving() {
		if (savingState != null) Condensation.logError("Unsaved already in saving state");
		savingState = state;
		state = new State();
	}

	public void savingDone() {
		if (savingState == null) Condensation.logError("Unsaved not in saving state");
		savingState = null;
	}

	public void savingFailed() {
		state.merge(savingState);
		savingState = null;
	}

	// *** Store interface

	@Override
	public void get(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull final GetDone done) {
		final CondensationObject stateObject = state.objects.get(hash);
		if (stateObject != null) {
			Condensation.mainThread.post(new Runnable() {
				@Override
				public void run() {
					done.onGetDone(stateObject);
				}
			});
			return;
		}

		if (savingState != null) {
			final CondensationObject savingStateObject = savingState.objects.get(hash);
			if (savingStateObject != null) {
				Condensation.mainThread.post(new Runnable() {
					@Override
					public void run() {
						done.onGetDone(savingStateObject);
					}
				});
				return;
			}
		}

		store.get(hash, keyPair, done);
	}

	@Override
	public void book(@NonNull Hash hash, @NonNull KeyPair keyPair, @NonNull BookDone done) {
		store.book(hash, keyPair, done);
	}

	@Override
	public void put(@NonNull Hash hash, @NonNull CondensationObject object, @NonNull KeyPair keyPair, @NonNull PutDone done) {
		store.put(hash, object, keyPair, done);
	}

	@Override
	public void list(@NonNull Hash accountHash, @NonNull BoxLabel boxLabel, long timeout, @NonNull KeyPair keyPair, @NonNull ListDone done) {
		store.list(accountHash, boxLabel, timeout, keyPair, done);
	}

	@Override
	public void modify(@NonNull Collection<BoxAddition> additions, @NonNull Collection<BoxRemoval> removals, @NonNull KeyPair keyPair, @NonNull ModifyDone done) {
		store.modify(additions, removals, keyPair, done);
	}
}
