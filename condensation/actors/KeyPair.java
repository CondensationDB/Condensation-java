package condensation.actors;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;

import condensation.Condensation;
import condensation.crypto.AES256CTR;
import condensation.crypto.RSAKeyPairGenerator;
import condensation.crypto.RSAPrivateKey;
import condensation.serialization.Bytes;
import condensation.serialization.CondensationObject;
import condensation.serialization.Hash;
import condensation.serialization.HashAndKey;
import condensation.serialization.Record;
import condensation.stores.Store;
import condensation.stores.Transfer;

public class KeyPair {
	// *** Static ***

	public static KeyPair generate() {
		// Generate a new private key
		RSAPrivateKey rsaPrivateKey = RSAKeyPairGenerator.generate();

		// Serialize the public key
		Record record = new Record();
		record.add(BC.e).add(rsaPrivateKey.e.toByteArray());
		record.add(BC.n).add(rsaPrivateKey.n.toByteArray());
		PublicKey publicKey = PublicKey.from(record.toObject());

		// Return a new instance
		return new KeyPair(publicKey, rsaPrivateKey);
	}

	public static KeyPair fromHex(String hex) {
		return from(Record.from(CondensationObject.from(Bytes.fromHex(hex))));
	}

	public static KeyPair from(Record record) {
		if (record == null) return null;
		PublicKey publicKey = PublicKey.from(CondensationObject.from(record.child(BC.public_key_object).bytesValue()));
		if (publicKey == null) return null;

		Record rsaKey = record.child(BC.rsa_key);
		BigInteger e = bigIntegerFromBytes(rsaKey.child(BC.e).bytesValue());
		BigInteger p = bigIntegerFromBytes(rsaKey.child(BC.p).bytesValue());
		BigInteger q = bigIntegerFromBytes(rsaKey.child(BC.q).bytesValue());
		if (e == null) return null;
		if (p == null) return null;
		if (q == null) return null;

		return new KeyPair(publicKey, new RSAPrivateKey(e, p, q));
	}

	private static BigInteger bigIntegerFromBytes(Bytes bytes) {
		byte[] byteArray = bytes.toByteArray();
		if (byteArray.length < 1) return null;
		return new BigInteger(1, byteArray);
	}

	// *** Object ***
	public final PublicKey publicKey;
	public final RSAPrivateKey rsaPrivateKey;

	public KeyPair(PublicKey publicKey, RSAPrivateKey rsaPrivateKey) {
		this.publicKey = publicKey;
		this.rsaPrivateKey = rsaPrivateKey;
	}

	// *** Serialization

	public Record toRecord() {
		Record record = new Record();
		record.add(BC.public_key_object).add(publicKey.object.toBytes());
		Record rsaKeyRecord = record.add(BC.rsa_key);
		rsaKeyRecord.add(BC.e).add(rsaPrivateKey.e.toByteArray());
		rsaKeyRecord.add(BC.p).add(rsaPrivateKey.p.toByteArray());
		rsaKeyRecord.add(BC.q).add(rsaPrivateKey.q.toByteArray());
		return record;
	}

	public String toHex() {
		CondensationObject object = toRecord().toObject();
		return object.header.asHex() + object.data.asHex();
	}

	// *** Private key interface

	public byte[] decrypt(byte[] bytes) {
		return rsaPrivateKey.decrypt(bytes);
	}

	public byte[] decrypt(Bytes bytes) {
		return rsaPrivateKey.decrypt(bytes.toByteArray());
	}

	public Bytes sign(byte[] digest) {
		return rsaPrivateKey.sign(digest, 0, digest.length);
	}

	public Bytes sign(Bytes digest) {
		return rsaPrivateKey.sign(digest.buffer, digest.byteOffset, digest.byteLength);
	}

	public Bytes sign(Hash hash) {
		return sign(hash.bytes);
	}

	// *** Envelope creation

	public Record createPublicEnvelope(Hash contentHash) {
		Record envelope = new Record();
		envelope.add(BC.content).add(contentHash);
		envelope.add(BC.signature).add(sign(contentHash));
		return envelope;
	}

	public Record createPrivateEnvelope(HashAndKey contentHashAndKey, ArrayList<PublicKey> recipientPublicKeys) {
		Record envelope = new Record();
		envelope.add(BC.content).add(contentHashAndKey.hash);
		addRecipientsToEnvelope(envelope, contentHashAndKey.key, recipientPublicKeys);
		envelope.add(BC.signature).add(sign(contentHashAndKey.hash));
		return envelope;
	}

	public Record createMessageEnvelope(String storeUrl, Record messageRecord, ArrayList<PublicKey> recipientPublicKeys, long expires) {
		Record contentRecord = new Record();
		contentRecord.add(BC.store).add(storeUrl);
		contentRecord.add(BC.sender).add(publicKey.hash);
		contentRecord.add(messageRecord.children);
		CondensationObject contentObject = contentRecord.toObject();

		byte[] contentKeyByteArray = Condensation.randomByteArray(32);
		AES256CTR aes = new AES256CTR(contentKeyByteArray);
		Bytes contentObjectBytes = contentObject.toBytes();
		Bytes encryptedContent = new Bytes(contentObjectBytes.byteLength);
		aes.crypt(contentObjectBytes, encryptedContent);
		Bytes contentKey = new Bytes(contentKeyByteArray);
		//Hash hashToSign = contentObject.calculateHash(); // prior to 2020-05-05
		Hash hashToSign = Hash.calculateFor(encryptedContent);

		Record envelope = new Record();
		envelope.add(BC.content).add(encryptedContent);
		addRecipientsToEnvelope(envelope, contentKey, recipientPublicKeys);
		envelope.add(BC.updated_by).add(publicKey.hash.bytes.slice(0, 24));
		envelope.add(BC.expires).add(expires);
		envelope.add(BC.signature).add(sign(hashToSign));
		return envelope;
	}

	private void addRecipientsToEnvelope(Record envelope, Bytes key, ArrayList<PublicKey> recipientPublicKeys) {
		Record encryptedKeyRecord = envelope.add(BC.encrypted_for);
		encryptedKeyRecord.add(publicKey.hash.bytes.slice(0, 24)).add(publicKey.encrypt(key));
		for (PublicKey publicKey : recipientPublicKeys) {
			if (publicKey.hash.equals(this.publicKey.hash)) continue;
			encryptedKeyRecord.add(publicKey.hash.bytes.slice(0, 24)).add(publicKey.encrypt(key));
		}
	}

	// *** Open envelopes

	public Bytes decryptKeyOnEnvelope(Record envelope) {
		// Read the AES key
		Bytes encryptedAesKey = envelope.child(BC.encrypted_for).child(publicKey.hash.bytes.slice(0, 24)).bytesValue();
		if (encryptedAesKey.byteLength < 1) return null;

		// Decrypt the AES key
		byte[] aesKeyBytes = decrypt(encryptedAesKey);
		if (aesKeyBytes == null || aesKeyBytes.length != 32) return null;

		return new Bytes(aesKeyBytes);
	}

	// *** Transfer

	public void transfer(Collection<Hash> hashes, Store source, Store destination, Transfer.Done done) {
		new Transfer(this, hashes, source, destination, done);
	}

	// *** Equality

	@Override
	public boolean equals(Object that) {
		return that instanceof KeyPair && (this == that || equals((KeyPair) that));
	}

	public boolean equals(KeyPair that) {
		if (that == null) return false;
		if (that == this) return true;
		return publicKey.hash.equals(that.publicKey.hash);
	}

	@Override
	public int hashCode() {
		return publicKey.hash.hashCode();
	}
}
