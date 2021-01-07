package condensation.stores.http;

import java.util.ArrayList;
import java.util.HashMap;

import condensation.Condensation;

public class HTTPStoreManager {
	// Module configuration
	public static final int connectTimeout = (int) Condensation.SECOND * 10;
	public static final int readTimeout = (int) Condensation.SECOND * 30;

	static final HashMap<String, HTTPStoreState> byStoreUrl = new HashMap<>();
	static int maintenanceCounter = 0;

	public static HTTPStoreState getOrCreate(String storeUrl) {
		HTTPStoreState storeState = byStoreUrl.get(storeUrl);
		if (storeState != null) {
			storeState.lastUsed = System.currentTimeMillis();
			maintenance();
			return storeState;
		}

		HTTPStoreState newStoreState = new HTTPStoreState(storeUrl);
		newStoreState.lastUsed = System.currentTimeMillis();
		byStoreUrl.put(storeUrl, newStoreState);
		maintenance();
		return newStoreState;
	}

	public static HTTPStoreState get(String storeUrl) {
		return byStoreUrl.get(storeUrl);
	}

	static void maintenance() {
		maintenanceCounter += 1;
		if (maintenanceCounter < 1024) return;
		maintenanceCounter = 0;

		long limit = System.currentTimeMillis() - Condensation.HOUR;
		ArrayList<HTTPStoreState> list = new ArrayList<>(byStoreUrl.values());
		for (HTTPStoreState item : list)
			if (item.lastUsed < limit) byStoreUrl.remove(item.storeUrl);
	}
}
