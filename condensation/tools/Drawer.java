package condensation.tools;

import android.graphics.Canvas;
import android.graphics.Paint;

import condensation.serialization.Record;

public class Drawer {
	public final CondensationView view;

	// State
	public Canvas canvas;
	public float top;
	public float bottom;
	public int width;

	public float y;

	Drawer(CondensationView view) {
		this.view = view;
	}

	public void title(String text) {
		canvas.drawText(text, view.style.left, y, view.style.titleText);
		y += view.style.lineHeight;
	}

	public void text(String text) {
		canvas.drawText(text, view.style.left, y, view.style.text);
		y += view.style.lineHeight;
	}

	public void text(String text, Paint style) {
		canvas.drawText(text, view.style.left, y, style);
		y += view.style.lineHeight;
	}

	public void text(String text, float x, int line, Paint style) {
		canvas.drawText(text, x, top + view.style.firstLine + line * view.style.lineHeight, style);
	}

	public void text(Iterable<String> lines) {
		for (String text : lines) text(text);
	}

	public void option(int n, String text, Paint textPaint) {
		int right = canvas.getWidth() - n * view.style.optionWidth;
		float useBottom = bottom - top > view.style.optionMaxHeight ? top + view.style.optionDefaultHeight : bottom;

		canvas.drawRect(right - view.style.optionWidth + 4, top + 2, right, useBottom - 4, view.style.optionFill);
		canvas.drawText(text, right - view.style.optionWidth * 0.5f, (top + useBottom) * 0.5f + 7, textPaint);
	}

	public void recordChildren(Record record, float x) {
		for (Record child : record.children)
			recordWithChildren(child, view.style.left + x);
	}

	public void record(Record record, float x) {
		recordWithChildren(record, view.style.left + x);
	}

	private void recordWithChildren(Record record, float left) {
		float x = left;
		if (record.bytes.byteLength == 0) {
			String value = "empty";
			canvas.drawText(value, x, y, view.style.grayText);
			x += view.style.text.measureText(value) + view.style.gap;
		} else {
			String value = Misc.interpret(record.bytes);
			canvas.drawText(value, x, y, view.style.text);
			x += view.style.text.measureText(value) + view.style.gap;

			String integerInterpretation = Misc.interpretInteger(record.bytes, view.now);
			if (integerInterpretation != null) {
				canvas.drawText(integerInterpretation, x, y, view.style.grayText);
				x += view.style.text.measureText(integerInterpretation) + view.style.gap;
			}
		}

		if (record.hash != null) {
			String value = "#";
			canvas.drawText(value, x, y, view.style.grayText);
			x += view.style.text.measureText(value) + view.style.gap;

			String hashHex = record.hash.shortHex();
			canvas.drawText(hashHex, x, y, view.style.blueText);
			x += view.style.text.measureText(hashHex) + view.style.gap;
		}

		y += view.style.lineHeight;

		for (Record child : record.children)
			recordWithChildren(child, left + view.style.indent);
	}
}
