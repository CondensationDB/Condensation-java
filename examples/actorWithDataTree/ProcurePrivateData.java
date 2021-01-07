package condensation.examples.actorWithDataTree;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.actors.PrivateRoot;
import condensation.actors.Source;
import condensation.dataTree.DataTree;
import condensation.tasks.AwaitCounter;

class ProcurePrivateData implements PrivateRoot.ProcureDone, DataTree.ReadDone, AwaitCounter.Done {
	public final ActorWithDataTree actor;
	final AwaitCounter awaitCounter = new AwaitCounter();
	boolean hasError = false;

	ProcurePrivateData(ActorWithDataTree actor) {
		this.actor = actor;
		actor.privateRoot.procure(Condensation.DAY, this);
	}

	@Override
	public void onPrivateRootProcureDone() {
		awaitCounter.await();
		actor.groupDataTree.read(this);
		awaitCounter.await();
		actor.localDataTree.read(this);
		awaitCounter.then(this);
		actor.onProcurePrivateDataDone();
	}

	@Override
	public void onPrivateRootProcureInvalidEntry(@NonNull Source source, @NonNull String reason) {
	}

	@Override
	public void onPrivateRootProcureFailed() {
		actor.onProcurePrivateDataFailed();
	}

	@Override
	public void onDataTreeReadDone() {
		awaitCounter.done();
	}

	@Override
	public void onDataTreeReadFailed() {
		hasError = true;
	}

	@Override
	public void onAwaitCounterDone() {
		if (hasError) return;
		actor.privateDataReady = true;
	}
}
