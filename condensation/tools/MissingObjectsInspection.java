package condensation.tools;

import java.util.ArrayList;

import condensation.Condensation;
import condensation.stores.MissingObject;

public class MissingObjectsInspection extends Inspection {
	int count = 0;

	public MissingObjectsInspection(CondensationView view) {
		super(view);
		setLines(2);
	}

	@Override
	public void update() {
		count = Condensation.missingObjectReporter.last.size();
		setLines(count < 1 ? 0 : 2);
	}

	@Override
	public void draw(Drawer drawer) {
		if (count < 1) return;
		drawer.text("Missing objects", view.style.redTitleText);
		drawer.text(count + (count == 1 ? " transfer" : " transfers") + " failed because of missing objects.", view.style.redText);
	}

	@Override
	public void onClick(float x, float y) {
		view.open(this);
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		ArrayList<Inspection> children = new ArrayList<>();
		for (MissingObject missingObject : Condensation.missingObjectReporter.last)
			children.add(new MissingObjectInspection(view, missingObject));
		return children;
	}
}
