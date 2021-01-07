package condensation.tasks;

import condensation.Condensation;

public abstract class LongLazyActionWithExponentialBackoff {
	public final long delay;
	public final float multiplier;
	public final long maximumDelay;

	private boolean shouldRunAgain = false;
	private Action currentAction = null;

	public LongLazyActionWithExponentialBackoff(long delay, float multiplier, long maximumDelay) {
		this.delay = delay;
		this.multiplier = multiplier;
		this.maximumDelay = maximumDelay;
	}

	public void schedule() {
		Condensation.assertMainThread();
		shouldRunAgain = true;
		if (currentAction != null) return;
		currentAction = new Action();
		Condensation.mainThread.postDelayed(currentAction, delay);
	}

	public void reschedule() {
		reschedule(delay);
	}

	public void reschedule(long delay) {
		Condensation.assertMainThread();
		shouldRunAgain = true;

		// Make sure the action is running only once
		if (currentAction != null && currentAction.isRunning) {
			currentAction.delay = this.delay;
			return;
		}

		currentAction = new Action();
		Condensation.mainThread.postDelayed(currentAction, delay);
	}

	protected abstract void action(Action action);

	public class Action implements Runnable {
		boolean isRunning = false;
		long delay = LongLazyActionWithExponentialBackoff.this.delay;

		@Override
		public void run() {
			if (this != currentAction) return;
			shouldRunAgain = false;
			isRunning = true;
			action(this);
		}

		public void done() {
			if (this != currentAction || !isRunning) {
				Condensation.logError("LongLazyActionWithExponentialBackoff.failed() called on an obsolete action.");
				return;
			}

			isRunning = false;
			delay = LongLazyActionWithExponentialBackoff.this.delay;
			if (shouldRunAgain) Condensation.mainThread.postDelayed(this, delay);
			else currentAction = null;
		}

		public void failed() {
			if (this != currentAction || !isRunning) {
				Condensation.logError("LongLazyActionWithExponentialBackoff.failed() called on an obsolete action.");
				return;
			}

			isRunning = false;
			delay *= multiplier;
			if (delay > maximumDelay) delay = maximumDelay;
			Condensation.mainThread.postDelayed(this, delay);
		}
	}
}
