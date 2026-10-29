package br.com.phs.coleta;

import static org.junit.Assert.*;

import android.view.View;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class LayoutTest {
  @Test
  public void activityLaunchesWithoutCameraPermission() {
    org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .denyPermissions(android.Manifest.permission.CAMERA);
    org.robolectric.android.controller.ActivityController<MainActivity> controller =
        org.robolectric.Robolectric.buildActivity(MainActivity.class).setup();
    try {
      assertNotNull(controller.get().getWindow().getDecorView());
    } finally {
      controller.pause().stop().destroy();
    }
  }

  @Test
  public void phoneAndTabletPreserveSameProportionsWithoutOverflow() {
    for (int[] size : new int[][] {{360, 640}, {393, 820}, {800, 1280}, {1280, 800}}) {
      FitLayout root = new FitLayout(RuntimeEnvironment.getApplication());
      root.addView(new LinearLayout(RuntimeEnvironment.getApplication()));
      root.measure(
          View.MeasureSpec.makeMeasureSpec(size[0], View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(size[1], View.MeasureSpec.EXACTLY));
      root.layout(0, 0, size[0], size[1]);
      View canvas = root.getChildAt(0);
      float width = canvas.getWidth() * canvas.getScaleX(),
          height = canvas.getHeight() * canvas.getScaleY();
      assertEquals(canvas.getScaleX(), canvas.getScaleY(), .0001f);
      assertEquals(380f / 800, width / height, .0001f);
      assertTrue(width <= size[0] + 1);
      assertTrue(height <= size[1] + 1);
      assertTrue(canvas.getLeft() >= 0);
      assertTrue(canvas.getTop() >= 0);
    }
  }
}
