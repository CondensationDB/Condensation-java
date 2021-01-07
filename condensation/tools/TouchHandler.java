package condensation.tools;

import android.view.MotionEvent;

class TouchHandler {
	final CondensationView view;

	// State
	boolean isTouching = false;
	float startX;
	float startY;
	long startTime;
	float previousX;
	float previousY;

	TouchHandler(CondensationView view) {
		this.view = view;
	}

	boolean onTouchEvent(MotionEvent event) {
		int action = event.getActionMasked();

		if (action == MotionEvent.ACTION_DOWN) {
			if (isTouching) return false;
			startX = event.getRawX();
			startY = event.getRawY();
			startTime = event.getEventTime();
			previousX = startX;
			previousY = startY;
			isTouching = true;
			return move(event);
		} else if (action == MotionEvent.ACTION_MOVE) {
			if (!isTouching) return false;
			return move(event);
		} else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
			if (!isTouching) return false;
			handleClick(event);
			isTouching = false;
		}

		return false;
	}

	boolean move(MotionEvent event) {
		float touchY = event.getRawY();
		view.onTouchScroll(touchY - previousY);
		previousY = touchY;
		return true;
	}

	boolean handleClick(MotionEvent event) {
		long duration = event.getEventTime() - startTime;
		if (duration > 400) return false;
		float dx = event.getRawX() - startX;
		float dy = event.getRawY() - startY;
		float distance2 = dx * dx + dy * dy;
		if (distance2 > 1600) return false;
		view.onTouchClick(event.getX(), event.getY());
		return true;
	}
}

