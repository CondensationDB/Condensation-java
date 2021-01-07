package condensation.dataTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import condensation.serialization.Bytes;
import condensation.serialization.Record;
import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;
import condensation.tools.Misc;

public class SelectorInspection extends Inspection {
	final Selector selector;
	final String title;

	// State
	long revision = -1;
	int countChildren = 0;
	Record record;
	HashMap<Bytes, SelectorInspection> children = new HashMap<>();

	// Interaction state
	boolean showOptions = false;
	boolean openSubDataTree = false;
	ArrayList<Inspection> subDataTreeItems;
	ArrayList<Inspection> childrenItems;

	public SelectorInspection(CondensationView view, Selector selector, String title) {
		super(view);
		this.selector = selector;
		this.title = title;
		sortKey = selector.label;
		setLines(1);
		update();
	}

	@Override
	public void update() {
		long newRevision = selector.revision();
		int newCountChildren = selector.countChildren();
		if (newRevision == revision && newCountChildren == countChildren) return;

		revision = newRevision;
		record = selector.record();
		countChildren = newCountChildren;

		int rightLines = 0;
		if (revision > 0) rightLines += 1;
		if (countChildren > 0) rightLines += 1;
		setLines(Math.max(record.countEntries(), rightLines));

		view.itemsChanged();
	}

	@Override
	public void draw(Drawer drawer) {
		if (showOptions) {
			// Options
			drawer.option(1, "✖", view.style.centeredOrangeText);
			drawer.option(2, "CLR", view.style.centeredRedText);
			drawer.option(3, "SUB", openSubDataTree ? view.style.centeredBlueText : view.style.centeredGrayText);
		} else {
			// Revision
			float y = drawer.y;
			float right = drawer.width - view.style.optionWidth - view.style.gap;
			if (revision > 0) {
				drawer.canvas.drawText(Misc.relativeTime(revision - view.now), right, y, view.style.rightGrayText);
				y += view.style.lineHeight;
			}

			// Children
			if (countChildren > 0) {
				int shown = children.size();
				drawer.canvas.drawText((countChildren == 1 ? "1 child" : countChildren + " children") + (shown > 0 && shown < countChildren ? ", " + shown + " shown" : ""), right, y, view.style.rightGrayText);
				y += view.style.lineHeight;
			}
		}

		// Main option
		drawer.option(0, "○", view.style.centeredGrayText);

		// Selector label
		drawer.title(title);

		// Records
		drawer.recordChildren(record, 0);
	}

	@Override
	public void onClick(float x, float y) {
		if (hitTestOption(x, y, 0)) {
			showOptions = !showOptions;
			view.invalidate();
		} else if (showOptions && hitTestOption(x, y, 1)) {
			selector.forget();
			showOptions = false;
			view.invalidate();
			update();
		} else if (showOptions && hitTestOption(x, y, 2)) {
			long revision = selector.revision();
			if (revision <= 0) return;
			selector.clear();
			//selector.setUnsigned(System.currentTimeMillis());
			showOptions = false;
			view.invalidate();
			update();
		} else if (showOptions && hitTestOption(x, y, 3)) {
			openSubDataTree = !openSubDataTree;
			view.open(this);
		} else {
			view.open(this);
		}
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		if (openSubDataTree) {
			if (subDataTreeItems == null) {
				subDataTreeItems = new ArrayList<>();
				subDataTreeItems.add(new SubDataTreeInspection(view, selector));
			}
			return subDataTreeItems;
		} else {
			int limit = 100;
			boolean hasChanges = childrenItems == null;
			for (Selector child : selector.children()) {
				limit -= 1;
				if (limit < 0) break;
				if (children.containsKey(child.label)) continue;
				children.put(child.label, new SelectorInspection(view, child, Misc.interpret(child.label)));
				hasChanges = true;
			}

			if (hasChanges) {
				childrenItems = new ArrayList<Inspection>(children.values());
				Collections.sort(childrenItems);
			}

			return childrenItems;
		}
	}

	@Override
	public void onClose() {
		children.clear();
		subDataTreeItems = null;
		childrenItems = null;
	}
}