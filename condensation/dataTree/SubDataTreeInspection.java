package condensation.dataTree;

import java.util.ArrayList;

import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;

class SubDataTreeInspection extends Inspection implements BranchListener {
	final SubDataTree dataTree;
	final ArrayList<Inspection> items = new ArrayList<>();

	SubDataTreeInspection(CondensationView view, Selector parent) {
		super(view);
		dataTree = new SubDataTree(parent);
		items.add(new SelectorInspection(view, dataTree.root, "Root"));
		setLines(5);
	}

	@Override
	public void onAttach() {
		dataTree.attach();
		dataTree.root.trackBranch(this);
	}

	@Override
	public void onDetach() {
		dataTree.detach();
		dataTree.root.untrackBranch(this);
	}

	@Override
	public void update() {
	}

	@Override
	public void draw(Drawer drawer) {
		//drawer.canvas.drawRect(0, drawer.top, drawer.width, drawer.top + 2, view.style.optionFill);
		drawer.text("");
		drawer.text("");
		drawer.text("");
		drawer.title("Sub data tree");
		drawer.text(dataTree.parts.size() + " parts, " + dataTree.itemsBySelector.size() + " items, " + dataTree.changes.count + " unsaved changes");
		drawer.option(0, "SAVE", view.style.centeredGrayText);
	}

	@Override
	public void onClick(float x, float y) {
		if (hitTestOption(x, y, 0)) {
			dataTree.save(DataTree.ignoreSaveDone);
		} else {
			view.open(this);
		}
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return items;
	}

	@Override
	public void onBranchChanged(Iterable<Selector> selectors) {
		view.itemsChanged();
	}
}
