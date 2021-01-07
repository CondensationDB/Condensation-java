package condensation.unionList;

import androidx.annotation.NonNull;
import condensation.serialization.Bytes;
import condensation.serialization.Record;

public abstract class Item<I extends Item> {
	@NonNull
	public final UnionList<I> unionList;
	@NonNull
	public final Bytes id;

	// State
	@NonNull
	protected Part part;

	public Item(@NonNull UnionList<I> unionList, @NonNull Bytes id) {
		this.unionList = unionList;
		this.id = id;
		part = unionList.changes;
		unionList.changes.count += 1;
		unionList.changeNotification.schedule();
	}

	protected abstract void addToRecord(@NonNull Record record);

	protected void setPart(Part part) {
		this.part.count -= 1;
		this.part = part;
		this.part.count += 1;
		if (part == unionList.changes) unionList.changeNotification.schedule();
	}
}
