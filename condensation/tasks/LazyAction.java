package condensation.tasks;

import condensation.Condensation;

public abstract class LazyAction {
	public final long delay;
	private Run scheduled = null;

	public LazyAction(long delay) {
		this.delay = delay;
	}

	public void schedule() {
		Condensation.assertMainThread();
		if (scheduled != null) return;
		scheduled = new Run();
		Condensation.mainThread.postDelayed(scheduled, delay);
	}

	public void runNow() {
		scheduled = null;
		action();
	}

	public boolean isScheduled() {
		return scheduled != null;
	}

	class Run implements Runnable {
		@Override
		public void run() {
			if (scheduled != this) return;
			scheduled = null;
			action();
		}
	}

	protected abstract void action();
}
