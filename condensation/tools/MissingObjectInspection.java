package condensation.tools;

import java.util.ArrayList;

import condensation.Condensation;
import condensation.serialization.Hash;
import condensation.stores.MissingObject;

public class MissingObjectInspection extends Inspection {
	final MissingObject missingObject;

	public MissingObjectInspection(CondensationView view, MissingObject missingObject) {
		super(view);
		this.missingObject = missingObject;
		setLines(2 + missingObject.path.size());
	}

	@Override
	public void update() {
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.text("In context \"" + missingObject.context + "\"", view.style.redTitleText);
		drawer.text("Missing " + missingObject.hash.shortHex(), view.style.redText);
		for (Hash hash : missingObject.path)
			drawer.text("  linked through " + hash.shortHex(), view.style.redText);
		drawer.option(0, "✖", view.style.centeredGrayText);
	}

	@Override
	public void onClick(float x, float y) {
		if (hitTestOption(x, y, 0)) {
			Condensation.missingObjectReporter.last.remove(missingObject);
			view.update();
		}
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return noChildren;
	}
}
