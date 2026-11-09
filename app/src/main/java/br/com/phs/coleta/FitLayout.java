package br.com.phs.coleta;

import android.content.Context;
import android.view.*;

/** Responsive canvas: fill the available area while uniformly scaling fixed-size controls. */
final class FitLayout extends ViewGroup {
  private final int designWidth, designHeight;
  private float scale = 1;
  private int canvasWidth, canvasHeight;

  FitLayout(Context context) {
    super(context);
    float d = getResources().getDisplayMetrics().density;
    designWidth = Math.round(380 * d);
    designHeight = Math.round(800 * d);
  }

  @Override
  protected void onMeasure(int w, int h) {
    setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
    int width = Math.max(1, getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
    int height = Math.max(1, getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
    scale = Math.min(width / (float) designWidth, height / (float) designHeight);
    // Expand the logical canvas instead of leaving unused bands around a fixed portrait page.
    // Weighted rows distribute the extra width/height; camera and icons keep a uniform scale.
    canvasWidth = Math.round(width / scale);
    canvasHeight = Math.round(height / scale);
    if (getChildCount() > 0)
      getChildAt(0)
          .measure(
              MeasureSpec.makeMeasureSpec(canvasWidth, MeasureSpec.EXACTLY),
              MeasureSpec.makeMeasureSpec(canvasHeight, MeasureSpec.EXACTLY));
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    if (getChildCount() == 0) return;
    View child = getChildAt(0);
    child.setPivotX(0);
    child.setPivotY(0);
    child.setScaleX(scale);
    child.setScaleY(scale);
    int x = getPaddingLeft(), y = getPaddingTop();
    child.layout(x, y, x + canvasWidth, y + canvasHeight);
  }
}
