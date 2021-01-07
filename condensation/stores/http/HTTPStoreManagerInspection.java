package condensation.stores.http;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import condensation.serialization.Bytes;
import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;

public class HTTPStoreManagerInspection extends Inspection {
	final ArrayList<Inspection> items = new ArrayList<>();
	final HashMap<String, HTTPStoreStateInspection> inspections = new HashMap<>();

	public HTTPStoreManagerInspection(CondensationView view) {
		super(view);
		this.sortKey = Bytes.fromUnsigned(1);
		setLines(2);
	}

	@Override
	public void update() {
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.title("HTTP Stores");
		drawer.text(HTTPStoreManager.byStoreUrl.size() + " stores");
	}

	@Override
	public void onClick(float x, float y) {
		view.open(this);
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		boolean hasChanges = false;

		// Prepare the items to remove
		HashSet<String> toRemove = new HashSet<>();
		for (String key : inspections.keySet())
			toRemove.add(key);

		// Add new items
		for (HTTPStoreState state : HTTPStoreManager.byStoreUrl.values()) {
			HTTPStoreStateInspection inspection = inspections.get(state.storeUrl);
			if (inspection == null) {
				inspections.put(state.storeUrl, new HTTPStoreStateInspection(view, state));
				hasChanges = true;
			} else {
				toRemove.remove(state.storeUrl);
			}
		}

		// Remove obsolete items
		for (String key : toRemove) {
			inspections.remove(key);
			hasChanges = true;
		}

		// Rebuild the list if necessary
		if (hasChanges) {
			items.clear();
			for (HTTPStoreStateInspection inspection : inspections.values())
				items.add(inspection);
			Collections.sort(items);
		}

		return items;
	}
}
