package condensation.actors.messageBoxReader;

import condensation.actors.ActorOnStore;
import condensation.actors.Source;
import condensation.serialization.Record;
import condensation.tasks.AwaitCounter;

public class ReceivedMessage {
	public final Entry entry;
	public final AwaitCounter awaitCounter;
	public final Source source;
	public final Record envelope;
	public final String senderStoreUrl;
	public final ActorOnStore sender;
	public final Record content;

	ReceivedMessage(Entry entry, AwaitCounter awaitCounter, Source source, Record envelope, String senderStoreUrl, ActorOnStore sender, Record content) {
		this.entry = entry;
		this.awaitCounter = awaitCounter;
		this.source = source;
		this.envelope = envelope;
		this.senderStoreUrl = senderStoreUrl;
		this.sender = sender;
		this.content = content;
	}

	public void waitForSenderStore() {
		entry.waitingForStore = sender.store;
	}

	public void skip() {
		entry.processed = false;
	}
}
