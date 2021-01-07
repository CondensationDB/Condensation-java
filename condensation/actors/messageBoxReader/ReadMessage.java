package condensation.actors.messageBoxReader;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.ActorOnStore;
import condensation.actors.BC;
import condensation.actors.GetPublicKey;
import condensation.actors.PublicKey;
import condensation.actors.Source;
import condensation.crypto.AES256CTR;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.stores.BoxLabel;
import condensation.stores.Store;
import condensation.tasks.AwaitCounter;
import condensation.tasks.RateLimitedTaskQueue;

class ReadMessage implements Runnable, Store.GetDone, AwaitCounter.Done {
	final ReadMessageBox readMessageBox;
	final Entry entry;
	final Source source;
	final AwaitCounter awaitCounter = new AwaitCounter();

	// State
	RateLimitedTaskQueue taskQueue;
	Record envelope;
	Record content;
	Hash signedHash;
	CondensationObject contentObject;
	Hash senderHash;
	Store senderStore;
	String senderStoreUrl;
	RateLimitedTaskQueue senderTaskQueue;

	ReadMessage(ReadMessageBox readMessageBox, Entry entry) {
		this.readMessageBox = readMessageBox;
		this.entry = entry;
		this.source = new Source(readMessageBox.messageBoxReader.pool.keyPair, readMessageBox.messageBoxReader.actorOnStore, BoxLabel.MESSAGES, entry.hash);
		readMessageBox.awaitCounter.await();

		taskQueue = readMessageBox.taskQueue;
		taskQueue.enqueue(this);
	}

	@Override
	public void run() {
		// Get the envelope
		source.actorOnStore.store.get(source.hash, readMessageBox.messageBoxReader.pool.keyPair, this);
	}

	@Override
	public void onGetDone(@NonNull CondensationObject object) {
		// Parse the record
		envelope = Record.from(object);
		if (envelope == null) {
			invalid("Envelope is not a record.");
			return;
		}

		// Decrypt the key
		Bytes aesKey = readMessageBox.messageBoxReader.pool.keyPair.decryptKeyOnEnvelope(envelope);
		if (aesKey == null) {
			invalid("Not encrypted for us.");
			return;
		}

		// Read the embedded content object
		Bytes encryptedContent = envelope.child(BC.content).bytesValue();
		if (encryptedContent.byteLength < 1) {
			invalid("Missing content object.");
			return;
		}

		AES256CTR aes = new AES256CTR(aesKey.toByteArray());
		Bytes contentObjectBytes = new Bytes(encryptedContent.byteLength);
		aes.crypt(encryptedContent, contentObjectBytes);
		contentObject = CondensationObject.from(contentObjectBytes);
		if (contentObject == null) {
			invalid("Invalid content object.");
			return;
		}

		content = Record.from(contentObject);
		if (content == null) {
			invalid("Content object is not a record.");
			return;
		}

		// Verify the sender hash
		senderHash = content.child(BC.sender).hashValue();
		if (senderHash == null) {
			invalid("Missing sender hash.");
			return;
		}

		// Verify the sender store
		Record storeRecord = content.child(BC.store);
		if (storeRecord.children.isEmpty()) {
			invalid("Missing sender store.");
			return;
		}

		senderStoreUrl = storeRecord.textValue();
		senderStore = readMessageBox.delegate.onMessageBoxVerifyStore(entry.hash, envelope, senderStoreUrl);
		if (senderStore == null) {
			invalid("Invalid sender store.");
			return;
		}

		senderTaskQueue = readMessageBox.messageBoxReader.pool.taskQueue(senderStore);
		if (senderTaskQueue.size() > 32) {
			waitForSenderStore();
			return;
		}

		// Prepare signature verification
		signedHash = Hash.calculateFor(encryptedContent);

		// Use the account key if sender and recipient are the same
		if (Hash.equals(senderHash, readMessageBox.messageBoxReader.actorOnStore.publicKey.hash)) {
			continueWithPublicKey(readMessageBox.messageBoxReader.actorOnStore.publicKey);
			return;
		}

		// Reuse a cached public key
		PublicKey publicKey = readMessageBox.messageBoxReader.pool.publicKeyCache.get(senderHash);
		if (publicKey != null) {
			continueWithPublicKey(publicKey);
			return;
		}

		// If the store is the same, continue on the same task queue
		if (senderTaskQueue == readMessageBox.taskQueue) {
			new GetSenderPublicKey().run();
			return;
		}

		// Switch to the sender task queue
		taskQueue.done();
		taskQueue = senderTaskQueue;
		taskQueue.enqueue(new GetSenderPublicKey());
	}

	@Override
	public void onGetNotFound() {
		invalid("Envelope object not found.");
	}

	@Override
	public void onGetStoreError(@NonNull String error) {
		entry.processed = false;
		readMessageBox.failWithStoreError(error);
	}

	class GetSenderPublicKey implements Runnable, GetPublicKey.Done {
		@Override
		public void run() {
			// Retrieve the sender's public key from the sender's store
			new GetPublicKey(senderHash, senderStore, readMessageBox.messageBoxReader.pool.keyPair, this);
		}

		@Override
		public void onGetPublicKeyDone(@NonNull PublicKey publicKey) {
			readMessageBox.messageBoxReader.pool.publicKeyCache.add(publicKey);
			continueWithPublicKey(publicKey);
		}

		@Override
		public void onGetPublicKeyInvalid(@NonNull String reason) {
			invalid("Failed to retrieve the sender's public key: " + reason);
		}

		@Override
		public void onGetPublicKeyStoreError(@NonNull String error) {
			waitForSenderStore();
		}
	}

	void continueWithPublicKey(@NonNull PublicKey publicKey) {
		// Verify the signature
		if (!Condensation.verifyEnvelopeSignature(envelope, publicKey, signedHash)) {
			// For backwards compatibility with versions before 2020-05-05
			if (!Condensation.verifyEnvelopeSignature(envelope, publicKey, contentObject.calculateHash())) {
				invalid("Invalid signature.");
				return;
			} else {
				Condensation.log("Old message signature from " + senderHash.shortHex());
			}
		}

		// The envelope is valid
		ActorOnStore sender = new ActorOnStore(publicKey, senderStore);
		ReceivedMessage receivedMessage = new ReceivedMessage(entry, awaitCounter, source, envelope, senderStoreUrl, sender, content);
		readMessageBox.delegate.onMessageBoxEntry(receivedMessage);
		awaitCounter.then(this);
	}

	@Override
	public void onAwaitCounterDone() {
		readMessageBox.awaitCounter.done();
		taskQueue.done();
	}

	// The entry is invalid
	void invalid(String reason) {
		readMessageBox.delegate.onMessageBoxInvalidEntry(source, reason);
		readMessageBox.awaitCounter.done();
		taskQueue.done();
	}

	// Do not process this message now
	void waitForSenderStore() {
		entry.waitingForStore = senderStore;
		readMessageBox.awaitCounter.done();
		taskQueue.done();
	}
}
