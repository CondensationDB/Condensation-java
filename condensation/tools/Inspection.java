package condensation.tools;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import condensation.serialization.Bytes;

public abstract class Inspection implements Comparable<Inspection> {
	public static final ArrayList<Inspection> noChildren = new ArrayList<>();
	public final CondensationView view;

	// For use by the list
	public boolean inUse = false;
	public Bytes sortKey = Bytes.empty;
	public int height;
	int y;

	public Inspection(CondensationView view) {
		this.view = view;
	}

	// Called whenever the item is attached to the list.
	public void onAttach() {
	}

	// Called whenever the item is detached from the list (and perhaps destroyed).
	public void onDetach() {
	}

	// If the item is attached, called once per second to update the item. Return true if the item was modified and needs to be redrawn.
	// Note that update() is also called right after attach(), before the item is positioned and drawn.
	public abstract void update();

	// Called whenever redrawing is needed.
	public abstract void draw(Drawer drawer);

	// Called whenever the user clicks on the item.
	public abstract void onClick(float x, float y);

	// Called when the item is opened.
	public void onOpen() {
	}

	// If the item is open, called once per second to update the children. You may (modify and) return the same list as before.
	// Note that updateChildren() is also called right after onOpen(), before the item is positioned and drawn.
	public abstract ArrayList<Inspection> updateChildren();

	// Called when the item is closed.
	public void onClose() {
	}

	@Override
	public int compareTo(@NonNull Inspection that) {
		return sortKey.compareTo(that.sortKey);
	}

	protected void setLines(int n) {
		int newHeight = view.style.baseHeight + n * view.style.lineHeight;
		if (newHeight == height) return;
		height = newHeight;
		view.itemsChanged();
	}

	public void invalidate() {
		view.invalidate();
	}

	protected boolean hitTestOption(float x, float y, int n) {
		int right = view.getWidth() - n * view.style.optionWidth;
		int useHeight = height > view.style.optionMaxHeight ? view.style.optionDefaultHeight : height;
		return x <= right && x > right - view.style.optionWidth && y <= useHeight;
	}
}
