package condensation.tasks;

import condensation.Condensation;

public abstract class LongLazyAction implements Runnable {
	public final long delay;
	private boolean isScheduled = false;
	private boolean isRunning = false;

	public LongLazyAction(long delay) {
		this.delay = delay;
	}

	public void schedule() {
		Condensation.assertMainThread();
		if (isScheduled) return;
		isScheduled = true;
		if (isRunning) return;
		Condensation.mainThread.postDelayed(this, delay);
	}

	@Override
	public void run() {
		if (!isScheduled) return;
		isScheduled = false;
		isRunning = true;
		action();
	}

	public void done() {
		if (!isRunning) {
			Condensation.logError("LongLazyAction.done() called without a running action.");
			return;
		}

		isRunning = false;
		if (isScheduled) Condensation.mainThread.postDelayed(this, delay);
	}

	protected abstract void action();
}
