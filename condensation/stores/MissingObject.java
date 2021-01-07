package condensation.stores;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import condensation.Condensation;
import condensation.serialization.Hash;

public class MissingObject {
	public final Hash hash;
	public final Store store;
	public final ArrayList<Hash> path = new ArrayList<>();
	public String context = "";

	public MissingObject(Hash hash, Store store) {
		this.hash = hash;
		this.store = store;
	}

	public void report() {
		Condensation.missingObjectReporter.report(this);
	}

	@NonNull
	public String toString() {
		StringBuilder text = new StringBuilder("Missing object " + hash.hex());
		for (Hash hash : path)
			text.append("\nlinked through ").append(hash.hex());
		if (!context.isEmpty())
			text.append("\nin context: ").append(context);
		return text.toString();
	}
}
