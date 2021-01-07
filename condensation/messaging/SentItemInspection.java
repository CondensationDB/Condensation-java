package condensation.messaging;

import java.util.ArrayList;

import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;
import condensation.tools.Misc;

public class SentItemInspection extends Inspection {
	final SentItem item;
	final String title;

	// State
	long validUntil = 0;
	String text = "";

	// Interaction state
	boolean showOptions = false;

	public SentItemInspection(CondensationView view, SentItem item) {
		super(view);
		this.item = item;
		title = item.id.toString(32);
		sortKey = item.id;
		setLines(1);
		update();
	}

	@Override
	public void update() {
		validUntil = item.validUntil;
		text = item.message.children.isEmpty() ? "empty" :
				(item.message.countEntries() - 1) + " entries, " + Misc.byteSize(item.message.calculateSize());

		setLines(2);
		view.itemsChanged();
	}

	@Override
	public void draw(Drawer drawer) {
		if (showOptions) {
			// Options
			drawer.option(1, "CLR", view.style.centeredRedText);
		} else {
			// Valid until
			float y = drawer.y;
			float right = drawer.width - view.style.optionWidth - view.style.gap;
			if (validUntil > 0) {
				drawer.canvas.drawText(Misc.relativeTime(validUntil - view.now), right, y, view.style.rightGrayText);
				y += view.style.lineHeight;
			}
		}

		// Main option
		drawer.option(0, "○", view.style.centeredGrayText);

		// Text
		drawer.title(title);
		drawer.text(text);

		// Records
		//drawer.record(record, view.style.left + 10);
	}

	@Override
	public void onClick(float x, float y) {
		if (hitTestOption(x, y, 0)) {
			showOptions = !showOptions;
			view.invalidate();
		} else if (showOptions && hitTestOption(x, y, 1)) {
			if (item.validUntil <= 0) return;
			item.clear(validUntil + 1);
			showOptions = false;
			view.invalidate();
			update();
		} else {
			view.open(this);
		}
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return noChildren;
	}
}