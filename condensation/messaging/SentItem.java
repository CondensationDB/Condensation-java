package condensation.messaging;

import androidx.annotation.NonNull;
import condensation.serialization.Bytes;
import condensation.serialization.Hash;
import condensation.serialization.Record;
import condensation.unionList.Item;
import condensation.unionList.Part;

public class SentItem extends Item<SentItem> {
	long validUntil = 0L;
	Record message = new Record();

	SentItem(@NonNull SentList unionList, @NonNull Bytes id) {
		super(unionList, id);
	}

	long getValidUntil() {
		return validUntil;
	}

	Hash getEnvelopeHash() {
		return Hash.from(message.bytes);
	}

	Bytes getEnvelopeHashBytes() {
		return message.bytes;
	}

	Record getMessage() {
		return message;
	}

	@Override
	protected void addToRecord(@NonNull Record record) {
		record.add(id).add(validUntil).add(message);
	}

	public void set(long validUntil, Hash envelopeHash, Record messageRecord) {
		Record message = new Record(envelopeHash.bytes);
		message.add(messageRecord.children);
		merge(unionList.changes, Math.max(validUntil, this.validUntil + 1), message);
	}

	public void clear(long validUntil) {
		merge(unionList.changes, Math.max(validUntil, this.validUntil + 1), new Record());
	}

	void merge(Part part, long validUntil, Record message) {
		if (this.validUntil > validUntil) return;
		if (this.validUntil == validUntil && part.size < this.part.size) return;
		this.validUntil = validUntil;
		this.message = message;
		setPart(part);
	}
}
