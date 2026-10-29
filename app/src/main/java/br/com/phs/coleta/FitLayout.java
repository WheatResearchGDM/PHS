package br.com.phs.coleta;

import android.content.Context;
import android.view.*;

/** A single portrait design canvas, uniformly scaled and centered on phones/tablets. */
final class FitLayout extends ViewGroup {
  private final int designWidth, designHeight;

  FitLayout(Context context) {
    super(context);
    float d = getResources().getDisplayMetrics().density;
    designWidth = Math.round(380 * d);
    designHeight = Math.round(800 * d);
  }

  @Override
  protected void onMeasure(int w, int h) {
    setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
    if (getChildCount() > 0)
      getChildAt(0)
          .measure(
              MeasureSpec.makeMeasureSpec(designWidth, MeasureSpec.EXACTLY),
              MeasureSpec.makeMeasureSpec(designHeight, MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    if (getChildCount() == 0) return;
    View child = getChildAt(0);
    int width = getWidth() - getPaddingLeft() - getPaddingRight(),
        height = getHeight() - getPaddingTop() - getPaddingBottom();
    float scale = Math.min(width / (float) designWidth, height / (float) designHeight);
    child.setPivotX(0);
    child.setPivotY(0);
    child.setScaleX(scale);
    child.setScaleY(scale);
    int x = getPaddingLeft() + Math.round((width - designWidth * scale) / 2),
        y = getPaddingTop() + Math.round((height - designHeight * scale) / 2);
    child.layout(x, y, x + designWidth, y + designHeight);
  }
}
