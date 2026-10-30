package br.com.phs.coleta;

import static org.junit.Assert.*;

import android.app.AlertDialog;
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
    AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
    ListView list = dialog.getListView();
    assertEquals(4, list.getAdapter().getCount());
    assertTrue(list.getAdapter().getItem(0).toString().contains("conexão direta"));
    assertEquals("Salvar como", list.getAdapter().getItem(3));
  }

  @Test
  public void directConnectionClearlyRequiresPublicRegistrationId() {
    OneDriveExportFlow flow =
        new OneDriveExportFlow(
            activity,
            activity.getSharedPreferences("settings", Context.MODE_PRIVATE),
            new byte[] {1},
            "test.xlsx",
            false,
            message -> {});
    try {
      flow.start();
      String labels = text(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView());
      assertTrue(labels.contains("ID público do aplicativo"));
      assertTrue(labels.contains("Não informe senha nem segredo"));
      assertFalse(
          activity
              .getSharedPreferences("settings", Context.MODE_PRIVATE)
              .contains("graph_session"));
    } finally {
      flow.close();
    }
  }
}
