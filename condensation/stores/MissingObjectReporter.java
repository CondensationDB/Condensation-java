package condensation.stores;

import android.util.Log;

import java.util.ArrayList;

public class MissingObjectReporter {
	public final ArrayList<MissingObject> last = new ArrayList<>();

	void report(MissingObject missingObject) {
		Log.e("Condensation", missingObject.toString());

		// Keep the last 10 errors
		last.add(missingObject);
		if (last.size() > 10) last.remove(0);
	}
}
