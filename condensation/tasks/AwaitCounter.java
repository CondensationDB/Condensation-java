package condensation.tasks;

import androidx.annotation.NonNull;

import condensation.Condensation;

public class AwaitCounter {
	private int awaiting = 0;
	private Done handler = null;

	public void await() {
		if (handler != null && awaiting == 0) Condensation.logError("This AwaitCounter has already completed.");
		awaiting += 1;
	}

	public void done() {
		awaiting -= 1;
		if (handler != null && awaiting == 0) handler.onAwaitCounterDone();
	}

	public void then(@NonNull Done handler) {
		if (this.handler != null) Condensation.logError("then() has already been called on this AwaitCounter.");
		this.handler = handler;
		if (awaiting == 0) handler.onAwaitCounterDone();
	}

	public interface Done {
		void onAwaitCounterDone();
	}
}
