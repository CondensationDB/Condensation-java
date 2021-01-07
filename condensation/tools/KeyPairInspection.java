package condensation.tools;

import java.util.ArrayList;

import condensation.actors.KeyPair;

public class KeyPairInspection extends Inspection {
	final String line1;
	final String line2;

	public KeyPairInspection(CondensationView view, KeyPair keyPair) {
		super(view);
		setLines(3);
		String hex = keyPair.publicKey.hash.hex();
		line1 = hex.substring(0, 32);
		line2 = hex.substring(32);
	}

	@Override
	public void update() {
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.title("Key pair");
		drawer.text(line1, view.style.monospaceText);
		drawer.text(line2, view.style.monospaceText);
	}

	@Override
	public void onClick(float x, float y) {
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return noChildren;
	}
}
