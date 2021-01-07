package condensation.tasks;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;

public class RateLimitedTaskQueue {
	public final int rate;
	private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
	private int running = 0;
	private Runnable handler;

	public RateLimitedTaskQueue(int rate) {
		this.rate = rate;
	}

	public int size() {
		return tasks.size();
	}

	public void enqueue(Runnable task) {
		tasks.add(task);
		runNextTask();
	}

	private void runNextTask() {
		if (running >= rate) return;
		if (tasks.isEmpty()) {
			if (running > 0) return;
			if (handler == null) return;
			handler.run();
			handler = null;
			return;
		}

		running += 1;
		tasks.remove().run();
	}

	public void done() {
		running -= 1;
		runNextTask();
	}

	public void then(@NonNull Runnable handler) {
		if (running == 0) handler.run();
		else this.handler = handler;
	}
}
