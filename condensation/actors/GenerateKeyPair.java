package condensation.actors;

import androidx.annotation.NonNull;

import condensation.Condensation;
import condensation.tasks.BackgroundTask;

public class GenerateKeyPair implements BackgroundTask {
	public final Done done;
	KeyPair keyPair = null;

	public GenerateKeyPair(Done done) {
		this.done = done;
		Condensation.computationExecutor.run(this);
	}

	@Override
	public void background() {
		keyPair = KeyPair.generate();
	}

	@Override
	public void after() {
		done.onKeyPairGenerated(keyPair);
	}

	public interface Done {
		void onKeyPairGenerated(@NonNull KeyPair keyPair);
	}
}
