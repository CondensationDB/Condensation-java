package condensation.stores.http;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RequestQueue implements Runnable {
	public final HTTPStoreState storeState;
	ExecutorService executor = Executors.newSingleThreadExecutor();
	ArrayList<Request> requests = new ArrayList<>();
	Request executingRequest = null;

	// For inspection purposes only
	Request currentOrLastRequest = null;

	RequestQueue(HTTPStoreState storeState) {
		this.storeState = storeState;
	}

	@Override
	public void run() {
		if (executingRequest != null) return;
		while (!requests.isEmpty()) {
			Request request = requests.remove(0);
			if (storeState.isDisabled()) {
				request.storeDisabled();
				continue;
			}

			if (!request.before()) continue;
			executingRequest = request;
			currentOrLastRequest = request;
			request.sent = System.currentTimeMillis();
			executor.execute(request);
		}
	}
}
