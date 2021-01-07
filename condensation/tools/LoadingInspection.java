package condensation.tools;

import java.util.ArrayList;

import condensation.actors.KeyPair;
import condensation.stores.Store;

abstract class LoadingInspection<T> extends Inspection {
	final KeyPair keyPair;
	final Store store;

	// State
	String title;
	boolean isLoading = false;
	boolean isOpen = false;
	String error = null;
	T result = null;
	ArrayList<Inspection> children = new ArrayList<>();

	LoadingInspection(CondensationView view, KeyPair keyPair, Store store) {
		super(view);
		this.keyPair = keyPair;
		this.store = store;
		setLines(2);
	}

	@Override
	public void onOpen() {
		isOpen = true;
		recreateChildren();
	}

	@Override
	public void onClose() {
		isOpen = false;
		children.clear();
	}

	@Override
	public void update() {
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.title(title);
		if (isLoading)
			drawer.text("Loading ...", view.style.blueText);
		else if (error != null)
			drawer.text(error, view.style.orangeText);
		else if (result != null)
			drawResult(drawer);
		else
			drawer.text("Tap to load", view.style.grayText);
	}

	@Override
	public void onClick(float x, float y) {
		if (result == null)
			load();
		else
			view.open(this);
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return children;
	}

	void setResult(T newResult) {
		isLoading = false;
		result = newResult;
		error = null;
		setLines(calculateResultLines());
		invalidate();
		recreateChildren();
	}

	void setError(String newError) {
		isLoading = false;
		result = null;
		error = newError;
		setLines(2);
		invalidate();
		recreateChildren();
	}

	void recreateChildren() {
		if (!isOpen) return;
		children.clear();
		if (result == null) return;
		addChildren();
	}

	protected void load() {
		if (isLoading) return;
		isLoading = true;
		invalidate();
		loadResult();
	}

	abstract void loadResult();

	abstract int calculateResultLines();

	abstract void drawResult(Drawer drawer);

	abstract void addChildren();
}
