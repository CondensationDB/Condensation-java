package condensation.tasks;

import condensation.Condensation;

public class BackgroundThread extends Thread {
	final BackgroundTask task;

	public BackgroundThread(BackgroundTask task) {
		this.task = task;
		start();
	}

	@Override
	public void run() {
		try {
			task.background();
			Condensation.mainThread.post(runAfter);
		} catch (Throwable th) {
			Condensation.logError("BackgroundThread exception", th);
		}
	}

	final Runnable runAfter = new Runnable() {
		@Override
		public void run() {
			task.after();
		}
	};
}
