package br.com.phs.coleta;

import static org.junit.Assert.*;

import android.content.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ExportUiTest {
  private ActivityController<MainActivity> controller;
  private MainActivity activity;

  @Before
  public void start() {
    Context c = RuntimeEnvironment.getApplication();
    c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .denyPermissions(android.Manifest.permission.CAMERA);
    controller = Robolectric.buildActivity(MainActivity.class).setup();
    activity = controller.get();
  }

  @After
  public void stop() {
    controller.pause().stop().destroy();
  }

  private String text(View v) {
    StringBuilder s = new StringBuilder();
    if (v instanceof TextView) s.append(((TextView) v).getText()).append('\n');
    if (v instanceof ViewGroup)
      for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
        s.append(text(((ViewGroup) v).getChildAt(i)));
    return s.toString();
  }

  @Test
  public void settingsDoesNotContainOutputDestination() throws Exception {
    Method m = MainActivity.class.getDeclaredMethod("settingsDialog");
    m.setAccessible(true);
    m.invoke(activity);
    String labels = text(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView());
    assertFalse(labels.contains("Destino da exportação"));
    assertFalse(labels.contains("Pasta selecionada"));
    assertTrue(labels.contains("Escolher arquivo de entrada"));
  }

  @Test
  public void destinationIsChosenForEveryExportEvenWithOldPreference() throws Exception {
    activity
        .getSharedPreferences("settings", Context.MODE_PRIVATE)
        .edit()
        .putString("outputMode", "folder")
        .commit();
    Field data = MainActivity.class.getDeclaredField("pendingExport");
    data.setAccessible(true);
    data.set(activity, new byte[] {1});
    Method m = MainActivity.class.getDeclaredMethod("chooseExportDestination", boolean.class);
    m.setAccessible(true);
    m.invoke(activity, false);
    Intent intent = Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;
    assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.getAction());
    assertTrue(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false));
  }

  @Test
  public void cloudDestinationIsRejectedBeforeWriting() throws Exception {
    Field data = MainActivity.class.getDeclaredField("pendingExport");
    data.setAccessible(true);
    data.set(activity, new byte[] {1});
    Method m = MainActivity.class.getDeclaredMethod("writeExport", android.net.Uri.class);
    m.setAccessible(true);
    m.invoke(
        activity,
        android.net.Uri.parse("content://com.microsoft.skydrive.documents/document/file"));
    Field busy = MainActivity.class.getDeclaredField("busy");
    busy.setAccessible(true);
    assertFalse(busy.getBoolean(activity));
    assertArrayEquals(new byte[] {1}, (byte[]) data.get(activity));
  }
}
