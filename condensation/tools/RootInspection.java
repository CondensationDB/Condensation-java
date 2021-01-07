package condensation.tools;

import android.graphics.Paint;

import java.util.ArrayList;

public class RootInspection extends Inspection {
	final ArrayList<Inspection> children = new ArrayList<>();
	final String title;
	final Paint linePaint = new Paint();

	public RootInspection(CondensationView view, String title) {
		super(view);
		this.title = title;
		setLines(3);

		linePaint.setStrokeWidth(2 * view.style.dp);
		linePaint.setColor(view.style.gray);
	}

	@Override
	public void update() {
	}

	@Override
	public void draw(Drawer drawer) {
		drawer.canvas.drawRect(0, drawer.top, drawer.width, drawer.bottom - 1, view.style.optionFill);
		drawer.text("Condensation Data System", view.style.left, 1, view.style.text);
		drawer.text(title, view.style.left, 2, view.style.titleText);
	}

	@Override
	public void onClick(float x, float y) {
		view.open(this);
	}

	@Override
	public ArrayList<Inspection> updateChildren() {
		return children;
	}

	public void addRoot(Inspection inspection) {
		children.add(inspection);
	}
}
