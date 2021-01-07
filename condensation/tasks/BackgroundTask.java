package condensation.tasks;

public interface BackgroundTask {
	// Implement this to execute stuff on a background thread.
	void background();

	// Implement this to execute stuff on the main thread after background() has finished.
	void after();
}
