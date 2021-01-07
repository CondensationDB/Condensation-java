package condensation.dataTree;

public interface BranchListener {
	void onBranchChanged(Iterable<Selector> selectors);
}
