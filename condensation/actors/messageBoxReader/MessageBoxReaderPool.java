package condensation.actors.messageBoxReader;

import java.util.HashMap;

import condensation.actors.KeyPair;
import condensation.actors.PublicKeyCache;
import condensation.stores.Store;
import condensation.tasks.RateLimitedTaskQueue;

public class MessageBoxReaderPool {
	public final KeyPair keyPair;

	// State
	final PublicKeyCache publicKeyCache;
	final HashMap<String, RateLimitedTaskQueue> taskQueues = new HashMap<>();

	public MessageBoxReaderPool(KeyPair keyPair, PublicKeyCache publicKeyCache) {
		this.keyPair = keyPair;
		this.publicKeyCache = publicKeyCache;
	}

	public RateLimitedTaskQueue taskQueue(Store store) {
		RateLimitedTaskQueue taskQueue = taskQueues.get(store.id);
		if (taskQueue != null) return taskQueue;
		RateLimitedTaskQueue newTaskQueue = new RateLimitedTaskQueue(4);
		taskQueues.put(store.id, newTaskQueue);
		return newTaskQueue;
	}
}
