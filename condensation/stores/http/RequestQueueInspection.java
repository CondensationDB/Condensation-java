package condensation.stores.http;

import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;

import condensation.Condensation;
import condensation.serialization.Bytes;
import condensation.tools.CondensationView;
import condensation.tools.Drawer;
import condensation.tools.Inspection;
import condensation.tools.Misc;

class RequestQueueInspection extends Inspection {
	final RequestQueue queue;
	final String title;

	// State
	long now;
	Request currentOrLastRequest = null;
	ArrayList<Request> requests = new ArrayList<>();

	RequestQueueInspection(CondensationView view, RequestQueue queue, String title, int order) {
		super(view);
		this.queue = queue;
		this.title = title;
		this.sortKey = Bytes.unsigned16(order);
		update();
	}

	@Override
	public void update() {
		now = System.currentTimeMillis();
		currentOrLastRequest = queue.currentOrLastRequest;
		if (currentOrLastRequest != null && currentOrLastRequest.received > 0 && now - currentOrLastRequest.received > 30 * Condensation.SECOND) currentOrLastRequest = null;
		requests = new ArrayList<>(queue.requests);
		Collections.sort(requests);

		int lines = 1 + requests.size();
		if (currentOrLastRequest != null) lines += 1;
		setLines(lines);
		view.itemsChanged();
	}

	@Override
	public void draw(Drawer drawer) {
		int x = drawer.width - view.style.left;
		drawer.canvas.drawText(requests.size() + " enqueued", x, drawer.y, view.style.rightText);
		drawer.title(title);

		if (currentOrLastRequest != null)
			drawRequest(drawer, currentOrLastRequest, x, true);
		for (Request request : requests)
			drawRequest(drawer, request, x, false);
	}

	void drawRequest(Drawer drawer, Request request, int x, boolean highlight) {
		long sentOrNow = request.sent > 0 ? request.sent : now;
		boolean isFinished = request.received > 0;
		long receivedOrNow = isFinished ? request.received : now;

		float enqueued = x + Math.round((request.enqueued - now) * 0.01f);
		float sent = x + Math.round((sentOrNow - now) * 0.01f);
		float received = x + Math.round((receivedOrNow - now) * 0.01f);
		if (isFinished && received - sent < 1) received = sent + 1;
		drawer.canvas.drawRect(enqueued, drawer.y - 10, sent, drawer.y, view.style.historyFill);
		drawer.canvas.drawRect(sent, drawer.y - 10, received, drawer.y, view.style.blueFill);

		Paint style = isFinished ? view.style.grayText : highlight ? view.style.blueText : view.style.text;
		String text = request.function  + " " + request.parameter;
		if (request.responseCode > 0) text += " ➔ " + request.responseCode;
		drawer.canvas.drawText(text, view.style.left, drawer.y, style);
		//drawer.canvas.drawText(request.parameter, view.style.left + view.style.dp * 60, drawer.y, style);
		//if (request.responseCode > 0) drawer.canvas.drawText("" + request.responseCode, x - view.style.dp * 50, drawer.y, view.style.rightGrayText);
		drawer.canvas.drawText(Misc.positiveDuration(now - request.enqueued), x, drawer.y, view.style.rightGrayText);

		drawer.y += view.style.lineHeight;
	}

	@Override
	public void onClick(float x, float y) {
		view.open(this);
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return noChildren;
	}
}
