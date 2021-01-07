package condensation.tools;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;

import condensation.Condensation;

public class CondensationView extends View {
	public final Style style;
	final TouchHandler touchHandler;
	public final Drawer drawer;

	// State
	float scrollTop = 100;
	float endY = 0;
	public long now;
	boolean updatesPaused = true;
	boolean updateScheduled = false;

	// Items
	ArrayList<Inspection> history = new ArrayList<>();
	ArrayList<Inspection> children = new ArrayList<>();
	final HashSet<Inspection> attachedItems = new HashSet<>();
	boolean needToRecalculatePositions = false;

	public CondensationView(Context context) {
		super(context);
		touchHandler = new TouchHandler(this);
		style = new Style(getResources());
		drawer = new Drawer(this);
	}

	public CondensationView(Context context, AttributeSet attrs) {
		super(context, attrs);
		touchHandler = new TouchHandler(this);
		style = new Style(getResources());
		drawer = new Drawer(this);
	}

	public CondensationView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		touchHandler = new TouchHandler(this);
		style = new Style(getResources());
		drawer = new Drawer(this);
	}

	public void setRoot(Inspection inspection) {
		for (Inspection item : attachedItems)
			item.onDetach();
		attachedItems.clear();
		children.clear();
		history.clear();

		history.add(inspection);
		needToRecalculatePositions = true;
		invalidate();
	}

	public void pause() {
		updatesPaused = true;
	}

	public void resume() {
		updatesPaused = false;
		if (updateScheduled) return;
		updateScheduled = true;
		update.run();
	}

	final Runnable update = new Runnable() {
		@Override
		public void run() {
			if (updatesPaused) {
				updateScheduled = false;
				return;
			}

			update();
			Condensation.mainThread.postDelayed(this, Condensation.SECOND);
		}
	};

	public Inspection head() {
		int index = history.size() - 1;
		if (index < 0) return null;
		return history.get(index);
	}

	public void update() {
		// Mark all children as not in use
		for (Inspection item : attachedItems)
			item.inUse = false;

		// Add the history
		for (Inspection item : history)
			update_use(item);

		// Update the children of the head item
		children = head().updateChildren();
		for (Inspection item : children)
			update_use(item);

		// Remove all children not in use any more
		ArrayList<Inspection> attached = new ArrayList<>(attachedItems);
		for (Inspection item : attached) {
			if (item.inUse) continue;
			item.onDetach();
			attachedItems.remove(item);
			itemsChanged();
		}
	}

	private void update_use(Inspection item) {
		if (!attachedItems.contains(item)) {
			attachedItems.add(item);
			item.onAttach();
			itemsChanged();
		}

		item.update();
		item.inUse = true;
	}

	public void itemsChanged() {
		needToRecalculatePositions = true;
		invalidate();
	}

	public void open(Inspection item) {
		// Same item
		if (head() == item) {
			update();
			return;
		}

		// Pop the stack
		int index = history.indexOf(item);
		if (index == history.size() - 1) {
			update();
			return;
		}

		if (index >= 0) {
			head().onClose();
			for (int i = history.size() - 1; i > index; i--)
				history.remove(i);
			head().onOpen();
			update();
			return;
		}

		// Push a new item onto the stack
		item.onAttach();
		head().onClose();
		history.add(item);
		head().onOpen();
		update();
	}

	public void recalculatePositions() {
		if (!needToRecalculatePositions) return;
		needToRecalculatePositions = false;

		// Add history
		int y = 0;
		for (Inspection item : history) {
			item.y = y;
			y += item.height;
		}

		// Add children
		for (Inspection item : children) {
			item.y = y;
			y += item.height;
		}

		endY = y;
	}

	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		recalculatePositions();

		scrollTop = Math.min(0, Math.max(-endY + getHeight() - 200 * style.dp, scrollTop));

		// Prepare
		now = System.currentTimeMillis();
		int width = getWidth();
		int height = getHeight();
		drawer.canvas = canvas;
		drawer.width = width;

		// Draw all children
		for (Inspection item : history)
			drawItem(canvas, width, height, item, true);
		for (Inspection item : children)
			drawItem(canvas, width, height, item, false);

	}

	private void drawItem(Canvas canvas, int width, int height, Inspection item, boolean isHistory) {
		float top = item.y + scrollTop;
		float bottom = top + item.height;
		if (bottom < 0 || top > height) return;
		drawer.top = top;
		drawer.bottom = bottom;
		drawer.y = top + style.firstLine;
		item.draw(drawer);

		canvas.drawLine(0, bottom - 1, width, bottom - 1, style.separatorStroke);
		if (isHistory) canvas.drawRect(0, top, 10, bottom, style.historyFill);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return touchHandler.onTouchEvent(event);
	}

	void onTouchClick(float x, float y) {
		float scrolledY = y - scrollTop;
		for (Inspection item : history) {
			if (scrolledY < item.y || scrolledY > item.y + item.height) continue;
			item.onClick(x, scrolledY - item.y);
			return;
		}

		for (Inspection item : children) {
			if (scrolledY < item.y || scrolledY > item.y + item.height) continue;
			item.onClick(x, scrolledY - item.y);
			return;
		}
	}

	void onTouchScroll(float dy) {
		scrollTop += dy;
		invalidate();
	}

	public boolean back() {
		int index = history.size() - 2;
		if (index < 0) return false;
		open((history.get(index)));
		return true;
	}
}
