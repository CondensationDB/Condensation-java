package condensation.tasks;

import java.util.concurrent.ExecutorService;

import condensation.Condensation;

public class BackgroundExecutor {
	public final ExecutorService executor;

	public BackgroundExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	public void run(BackgroundTask task) {
		new Execution(task);
	}

	class Execution implements Runnable {
		final BackgroundTask task;

		Execution(BackgroundTask task) {
			this.task = task;
			executor.execute(this);
		}

		@Override
		public void run() {
			try {
				task.background();
				Condensation.mainThread.post(runAfter);
			} catch (Throwable th) {
				Condensation.logError("BackgroundExecutor exception", th);
			}
		}

		final Runnable runAfter = new Runnable() {
			@Override
			public void run() {
				task.after();
			}
		};
	}
}
