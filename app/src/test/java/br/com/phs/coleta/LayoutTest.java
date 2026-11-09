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
  public void phoneAndTabletFillAvailableAreaWithUniformControlScale() {
    for (int[] size :
        new int[][] {{360, 640}, {393, 820}, {800, 1280}, {1200, 2000}, {1280, 800}}) {
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
      assertEquals(size[0], width, 1f);
      assertEquals(size[1], height, 1f);
      assertEquals(0, canvas.getLeft());
      assertEquals(0, canvas.getTop());
    }
  }

  @Test
  public void systemBarInsetsRemainOutsideResponsiveCanvas() {
    FitLayout root = new FitLayout(RuntimeEnvironment.getApplication());
    root.setPadding(12, 24, 18, 30);
    root.addView(new LinearLayout(RuntimeEnvironment.getApplication()));
    root.measure(
        View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(2000, View.MeasureSpec.EXACTLY));
    root.layout(0, 0, 1200, 2000);
    View canvas = root.getChildAt(0);
    assertEquals(12, canvas.getLeft());
    assertEquals(24, canvas.getTop());
    assertEquals(1170, canvas.getWidth() * canvas.getScaleX(), 1f);
    assertEquals(1946, canvas.getHeight() * canvas.getScaleY(), 1f);
  }
}
