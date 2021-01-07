package condensation.tools;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

public class Style {
	// Density
	public final float dp;
	public final float sp;

	// Distances
	public final int baseHeight;
	public final int firstLine;
	public final int lineHeight;
	public final int left;
	public final int optionWidth;
	public final int optionMaxHeight;
	public final int optionDefaultHeight;
	public final int gap;
	public final int indent;

	// Colors
	public final int gray = 0xff808080;
	public final int white50 = 0x80ffffff;
	public final int blue = 0xff2a7fff;
	public final int orange = 0xffff7f2a;
	public final int red = 0xffff0000;

	// Colors and styles
	public final Paint historyFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	public final Paint optionFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	public final Paint blueFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	public final Paint orangeFill = new Paint(Paint.ANTI_ALIAS_FLAG);
	public final Paint separatorStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	public final Paint titleText;
	public final Paint redTitleText;
	public final Paint text;
	public final Paint monospaceText;
	public final Paint grayText;
	public final Paint blueText;
	public final Paint orangeText;
	public final Paint redText;
	public final Paint rightText;
	public final Paint rightGrayText;
	public final Paint rightOrangeText;
	public final Paint centeredText;
	public final Paint centeredGrayText;
	public final Paint centeredBlueText;
	public final Paint centeredOrangeText;
	public final Paint centeredRedText;

	public Style(Resources resources) {
		dp = resources.getDisplayMetrics().density;
		sp = resources.getDisplayMetrics().scaledDensity;

		baseHeight = dpInt(16);
		firstLine = dpInt(22);
		lineHeight = dpInt(18);
		left = dpInt(15);
		optionWidth = dpInt(60);
		optionMaxHeight = dpInt(80);
		optionDefaultHeight = dpInt(60);
		gap = dpInt(8);
		indent = dpInt(10);

		historyFill.setARGB(64, 255, 255, 255);
		historyFill.setStyle(Paint.Style.FILL);

		optionFill.setARGB(255, 32, 32, 32);
		optionFill.setStyle(Paint.Style.FILL);

		blueFill.setColor(blue);
		blueFill.setStyle(Paint.Style.FILL);

		orangeFill.setColor(orange);
		orangeFill.setStyle(Paint.Style.FILL);

		separatorStroke.setARGB(255, 64, 64, 64);
		separatorStroke.setStyle(Paint.Style.STROKE);
		separatorStroke.setStrokeWidth(1);

		titleText = textPaint(Color.WHITE, 14, Paint.Align.LEFT);
		titleText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

		redTitleText = textPaint(red, 14, Paint.Align.LEFT);
		redTitleText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

		text = textPaint(Color.WHITE, 14, Paint.Align.LEFT);
		grayText = textPaint(white50, 14, Paint.Align.LEFT);
		blueText = textPaint(blue, 14, Paint.Align.LEFT);
		orangeText = textPaint(orange, 14, Paint.Align.LEFT);
		redText = textPaint(red, 14, Paint.Align.LEFT);

		rightText = textPaint(Color.WHITE, 14, Paint.Align.RIGHT);
		rightGrayText = textPaint(white50, 14, Paint.Align.RIGHT);
		rightOrangeText = textPaint(orange, 14, Paint.Align.RIGHT);

		centeredText = textPaint(Color.WHITE, 12, Paint.Align.CENTER);
		centeredGrayText = textPaint(white50, 12, Paint.Align.CENTER);
		centeredBlueText = textPaint(blue, 12, Paint.Align.CENTER);
		centeredOrangeText = textPaint(orange, 12, Paint.Align.CENTER);
		centeredRedText = textPaint(red, 12, Paint.Align.CENTER);

		monospaceText = textPaint(Color.WHITE, 13, Paint.Align.LEFT);
		monospaceText.setTypeface(Typeface.MONOSPACE);
	}

	public int dpInt(float value) {
		return Math.round(dp * value);
	}

	public Paint textPaint(int color, float size, Paint.Align alignment) {
		Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		paint.setColor(color);
		paint.setTextSize(size * sp);
		paint.setTextAlign(alignment);
		return paint;
	}
}
