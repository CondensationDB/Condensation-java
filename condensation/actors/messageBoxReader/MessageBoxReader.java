package condensation.actors.messageBoxReader;

import androidx.annotation.NonNull;

import java.util.HashMap;

import condensation.actors.ActorOnStore;
import condensation.actors.Source;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.Store;

public class MessageBoxReader {
	public final MessageBoxReaderPool pool;
	public final ActorOnStore actorOnStore;

	// State
	HashMap<Hash, Entry> entries = new HashMap<>();

	public MessageBoxReader(MessageBoxReaderPool pool, ActorOnStore actorOnStore) {
		this.pool = pool;
		this.actorOnStore = actorOnStore;
	}

	public void read(Delegate delegate) {
		new ReadMessageBox(this, delegate);
	}

	public interface Delegate {
		void onMessageBoxReadingDone();

		void onMessageBoxReadingFailed();

		Store onMessageBoxVerifyStore(@NonNull Hash envelopeHash, @NonNull Record envelope, @NonNull String storeUrl);

		void onMessageBoxEntry(@NonNull ReceivedMessage message);

		void onMessageBoxInvalidEntry(@NonNull Source source, @NonNull String reason);
	}
}
