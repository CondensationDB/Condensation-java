package condensation.messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import condensation.serialization.Bytes;
import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;

public class SentListInspection extends Inspection {
	final SentList list;
	final String title;

	// State
	int countItems;
	HashMap<Bytes, SentItemInspection> children = new HashMap<>();
	ArrayList<Inspection> childrenItems;

	public SentListInspection(CondensationView view, SentList list, String title) {
		super(view);
		this.list = list;
		this.title = title;
		setLines(2);
		update();
	}

	@Override
	public void update() {
		countItems = list.items().size();
		view.itemsChanged();
	}

	@Override
	public void draw(Drawer drawer) {
		// Selector label
		drawer.title(title);

		// Records
		drawer.text(countItems + " items");
	}

	@Override
	public void onClick(float x, float y) {
		view.open(this);
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		int limit = 100;
		boolean hasChanges = childrenItems == null;
		for (SentItem item : list.items()) {
			limit -= 1;
			if (limit < 0) break;
			if (children.containsKey(item.id)) continue;
			children.put(item.id, new SentItemInspection(view, item));
			hasChanges = true;
		}

		if (hasChanges) {
			childrenItems = new ArrayList<Inspection>(children.values());
			Collections.sort(childrenItems);
		}

		return childrenItems;
	}

	@Override
	public void onClose() {
		children.clear();
		childrenItems = null;
	}
}