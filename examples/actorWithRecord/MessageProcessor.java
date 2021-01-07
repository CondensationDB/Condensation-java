package condensation.examples.actorWithRecord;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.ActorOnStore;
import condensation.actors.PublicKeyCache;
import condensation.actors.Source;
import condensation.actors.messageBoxReader.MessageBoxReader;
import condensation.actors.messageBoxReader.MessageBoxReaderPool;
import condensation.actors.messageBoxReader.ReceivedMessage;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.Store;

public class MessageProcessor implements MessageBoxReader.Delegate {
	public final ActorWithRecord actor;
	public final MessageBoxReader messageBoxReader;

	public MessageProcessor(ActorWithRecord actor) {
		this.actor = actor;
		MessageBoxReaderPool pool = new MessageBoxReaderPool(actor.keyPair, new PublicKeyCache(128));
		messageBoxReader = new MessageBoxReader(pool, new ActorOnStore(actor.keyPair.publicKey, actor.messagingStore.store));
	}

	void read() {
		messageBoxReader.read(this);
	}

	@Override
	public void onMessageBoxReadingDone() {
	}

	@Override
	public void onMessageBoxReadingFailed() {
	}

	@Override
	public Store onMessageBoxVerifyStore(@NonNull Hash envelopeHash, @NonNull Record envelope, @NonNull String storeUrl) {
		return actor.store(storeUrl);
	}

	@Override
	public void onMessageBoxEntry(@NonNull ReceivedMessage message) {
		for (Record section : message.content.children) {
			if (section.bytes.equals(condensation.actors.BC.sender)) continue;
			if (section.bytes.equals(condensation.actors.BC.store)) continue;
			if (section.bytes.equals(BC.group_data)) actor.group.processGroupDataMessage(message, section);
		}
	}

	@Override
	public void onMessageBoxInvalidEntry(@NonNull Source source, @NonNull String reason) {
		Condensation.log("Invalid message " + source.toString() + ": " + reason);
	}
}
