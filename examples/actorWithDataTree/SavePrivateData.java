package condensation.examples.actorWithDataTree;

import condensation.actors.PrivateRoot;
import condensation.dataTree.DataTree;
import condensation.tasks.AwaitCounter;

public class SavePrivateData implements DataTree.SaveDone, AwaitCounter.Done, PrivateRoot.SavingDone {
	final ActorWithDataTree actor;
	final AwaitCounter awaitCounter = new AwaitCounter();

	SavePrivateData(ActorWithDataTree actor) {
		this.actor = actor;

		awaitCounter.await();
		actor.groupDataTree.save(this);
		awaitCounter.await();
		actor.localDataTree.save(this);
		awaitCounter.then(this);
	}

	@Override
	public void onDataTreeSaveDone() {
		awaitCounter.done();
	}

	@Override
	public void onDataTreeSaveFailed() {
		actor.onSavingFailed();
	}

	@Override
	public void onAwaitCounterDone() {
		actor.privateRoot.save(actor.entrustedKeys, this);
		actor.group.shareGroupData();
	}

	@Override
	public void onPrivateRootSavingDone() {
		actor.onSavingDone();
	}

	@Override
	public void onPrivateRootSavingFailed() {
		actor.onSavingFailed();
	}
}