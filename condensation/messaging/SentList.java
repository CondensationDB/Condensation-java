package condensation.messaging;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import condensation.Condensation;
import condensation.actors.BC;
import condensation.actors.PrivateRoot;
import condensation.serialization.Bytes;
import condensation.serialization.Record;
import condensation.unionList.Part;
import condensation.unionList.UnionList;

public class SentList extends UnionList<SentItem> {
	public SentList(PrivateRoot privateRoot) {
		super(privateRoot, BC.sent);
	}

	@NonNull
	@Override
	protected SentItem createItem(@NonNull Bytes id) {
		return new SentItem(this, id);
	}

	@Override
	protected void mergeRecord(Part part, Record record) {
		long limit = System.currentTimeMillis() + Condensation.YEAR;
		SentItem item = getOrCreate(record.bytes);
		for (Record child : record.children) {
			long validUntil = child.asInteger();
			if (validUntil > limit) validUntil = 0;
			item.merge(part, validUntil, child.firstChild());
		}
	}

	@Override
	protected void forgetObsoleteItems() {
		long now = System.currentTimeMillis();
		ArrayList<SentItem> toDelete = new ArrayList<>();
		for (SentItem item : items.values()) {
			if (item.validUntil >= now) continue;
			toDelete.add(item);
		}

		for (SentItem item : toDelete)
			forget(item);
	}
}
