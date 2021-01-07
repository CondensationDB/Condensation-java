package condensation.stores.http;

import java.util.ArrayList;

import condensation.Condensation;
import condensation.serialization.Bytes;
import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;
import condensation.tools.Misc;

public class HTTPStoreStateInspection extends Inspection {
	final HTTPStoreState state;
	final ArrayList<Inspection> items = new ArrayList<>();

	// State
	int executingRequests = 0;
	int waitingRequests = 0;
	String error = null;

	HTTPStoreStateInspection(CondensationView view, HTTPStoreState state) {
		super(view);
		this.state = state;
		this.sortKey = Bytes.fromText(state.storeUrl);
		setLines(3);
		update();

		items.add(new RequestQueueInspection(view, state.getQueue, "Retrieve Queue", 0));
		items.add(new RequestQueueInspection(view, state.putSmallQueue, "Small Send Queue", 1));
		items.add(new RequestQueueInspection(view, state.putLargeQueue, "Large Send Queue", 2));
	}

	@Override
	public void update() {
		executingRequests = (state.getQueue.executingRequest == null ? 0 : 1) + (state.putSmallQueue.executingRequest == null ? 0 : 1) + (state.putLargeQueue.executingRequest == null ? 0 : 1);
		waitingRequests = state.getQueue.requests.size() + state.putSmallQueue.requests.size() + state.putLargeQueue.requests.size();
		error = (state.lastNotReached > state.lastReached || state.lastNotReached > view.now - Condensation.MINUTE) && state.lastError != null ? state.lastError : null;
		setLines(error == null ? 3 : 4);
		view.itemsChanged();
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.title(state.storeUrl);
		String success = state.lastReached == 0 ? "no success" : "last success " + Misc.relativeTime(state.lastReached - view.now);
		String failure = state.lastNotReached == 0 ? "no failure" : "last failure " + Misc.relativeTime(state.lastNotReached - view.now);
		drawer.text(success + ", " + failure);
		drawer.text(executingRequests + " executing requests, " + waitingRequests + " waiting, " + state.requestsDone + " done");
		if (error != null) drawer.text(error, view.style.orangeText);
	}

	@Override
	public void onClick(float x, float y) {
		view.open(this);
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return items;
	}
}
