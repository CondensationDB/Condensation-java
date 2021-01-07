package condensation.serialization;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

import condensation.Condensation;
import condensation.crypto.AES256CTR;
import condensation.crypto.SHA256;

public class CondensationObject {
	// *** Static ***

	public static final Bytes emptyHeader = new Bytes(4);

	public static CondensationObject from(byte[] bytes) {
		return bytes == null ? null : from(new Bytes(bytes));
	}

	public static CondensationObject from(Bytes bytes) {
		if (bytes == null || bytes.byteLength < 4) return null;
		int hashesCount = bytes.getInteger32(0);
		int dataStart = hashesCount * 32 + 4;
		if (dataStart > bytes.byteLength) return null;
		Bytes header = bytes.slice(0, dataStart);
		Bytes data = bytes.slice(dataStart);
		return new CondensationObject(hashesCount, header, data);
	}

	public static CondensationObject from(File file) throws IOException {
		return from(Bytes.from(file));
	}

	public static CondensationObject create(Bytes header, Bytes data) {
		if (header.byteLength < 4) return null;
		int hashesCount = header.getInteger32(0);
		if (header.byteLength != 4 + hashesCount * 32) return null;
		return new CondensationObject(hashesCount, header, data);
	}

	// *** Object ***

	public final int hashesCount;
	public final Bytes header;
	public final Bytes data;

	public CondensationObject(int hashesCount, Bytes header, Bytes data) {
		this.hashesCount = hashesCount;
		this.header = header;
		this.data = data;
	}

	public Hash hashAtIndex(int index) {
		if (index < 0 || index >= hashesCount) return null;
		return Hash.from(header.slice(4 + index * 32, 32));
	}

	public Hash[] hashes() {
		Hash[] hashes = new Hash[hashesCount];
		for (int i = 0; i < hashesCount; i++)
			hashes[i] = Hash.from(header.slice(4 + i * 32, 32));
		return hashes;
	}

	public Hash calculateHash() {
		MessageDigest sha256 = SHA256.createInstance();
		sha256.update(header.buffer, header.byteOffset, header.byteLength);
		sha256.update(data.buffer, data.byteOffset, data.byteLength);
		return Hash.from(sha256.digest());
	}

	// Returns an en- or decrypted object.
	public CondensationObject crypt(byte[] key) {
		AES256CTR aes = new AES256CTR(key);
		Bytes encryptedData = new Bytes(data.byteLength);
		aes.crypt(data, encryptedData);
		return new CondensationObject(hashesCount, header, encryptedData);
	}

	public CondensationObject crypt(Bytes key) {
		return crypt(key.toByteArray());
	}

	// En- or decrypts the data in-place. This is slightly faster than crypt.
	public void cryptInplace(byte[] key) {
		AES256CTR aes = new AES256CTR(key);
		aes.crypt(data, data);
	}

	// En- or decrypts the data in-place.
	public void cryptInplace(Bytes key) {
		cryptInplace(key.toByteArray());
	}

	// Encrypts the data in-place with a random key.
	public Bytes cryptInplace() {
		byte[] key = Condensation.randomByteArray(32);
		cryptInplace(key);
		return new Bytes(key);
	}

	// Returns a copy of the bytes of this object. To avoid copying the bytes, consider using condensationObject.header and condensationObject.data.
	public Bytes toBytes() {
		return header.concatenate(data);
	}

	public int byteLength() {
		return header.byteLength + data.byteLength;
	}

	public CondensationObject clone() {
		Bytes bytes = header.concatenate(data);
		return new CondensationObject(hashesCount, bytes.slice(0, header.byteLength), bytes.slice(header.byteLength));
	}

	// Writes the object to a file
	public void writeToFile(File file) throws IOException {
		FileOutputStream stream = new FileOutputStream(file, false);
		writeTo(stream);
		stream.close();
	}

	public void writeTo(OutputStream stream) throws IOException {
		stream.write(header.buffer, header.byteOffset, header.byteLength);
		stream.write(data.buffer, data.byteOffset, data.byteLength);
	}
}
