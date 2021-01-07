package condensation.actors;

import java.util.HashMap;
import java.util.Map;

import condensation.actors.messageBoxReader.ReceivedMessage;
import condensation.serialization.Bytes;
import condensation.serialization.Record;

public class GroupData {
	final HashMap<Bytes, MergeableData> groupDataHandlers = new HashMap<>();

	public GroupData() {
	}

	public void add(Bytes label, MergeableData dataHandler) {
		groupDataHandlers.put(label, dataHandler);
	}

	public void removeGroupData(Bytes label, MergeableData dataHandler) {
		MergeableData registered = groupDataHandlers.get(label);
		if (registered != dataHandler) return;
		groupDataHandlers.remove(label);
	}

	// *** Sharing

	public Record createMessage() {
		Record message = new Record();
		Record data = message.add(condensation.actorWithDataTree.BC.group_data);
		for (Map.Entry<Bytes, MergeableData> entry : groupDataHandlers.entrySet())
			entry.getValue().addDataTo(data.add(entry.getKey()));
		return message;
	}

	// *** Receiving messages

	public void mergeMessage(ReceivedMessage message, Record section) {
		for (Record child : section.children) {
			MergeableData dataHandler = groupDataHandlers.get(child.bytes);
			if (dataHandler == null) continue;
			dataHandler.mergeExternalData(message.sender.store, child, message.source);
		}
	}

}
