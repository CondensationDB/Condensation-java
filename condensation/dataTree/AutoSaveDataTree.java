package condensation.dataTree;

import condensation.tasks.LongLazyActionWithExponentialBackoff;

public class AutoSaveDataTree extends LongLazyActionWithExponentialBackoff implements BranchListener {
	final DataTree dataTree;

	public AutoSaveDataTree(DataTree dataTree, long delay, float multiplier, long maximumDelay) {
		super(delay, multiplier, maximumDelay);
		this.dataTree = dataTree;
	}

	public void start() {
		dataTree.root.trackBranch(this);
	}

	public void stop() {
		dataTree.root.untrackBranch(this);
	}

	@Override
	public void onBranchChanged(Iterable<Selector> selectors) {
		reschedule();
	}

	@Override
	public void action(Action action) {
		new Save(action);
	}

	class Save implements DataTree.SaveDone {
		final Action action;

		Save(Action action) {
			this.action = action;
			dataTree.save(this);
		}

		@Override
		public void onDataTreeSaveDone() {
			action.done();
		}

		@Override
		public void onDataTreeSaveFailed() {
			// This is called if there is a technical problem (e.g. store not available),
			// or if another save operation (potentially issued by somebody else) is going on.
			action.failed();
		}
	}
}
