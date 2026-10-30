package br.com.phs.coleta;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.content.*;
import java.lang.reflect.*;
import java.time.LocalDate;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CollectionFlowTest {
  private ActivityController<MainActivity> controller;
  private MainActivity activity;
  private SharedPreferences prefs;
  private final Domain.Sample sample =
      new Domain.Sample("123", "TESTE", "LOCAL", "VARIEDADE", "42", "1");

  @Before
  public void setup() {
    Context c = RuntimeEnvironment.getApplication();
    c.deleteDatabase("phs.db");
    prefs = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    prefs.edit().clear().putString("user", "Maria").commit();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .denyPermissions(android.Manifest.permission.CAMERA);
    controller = Robolectric.buildActivity(MainActivity.class).setup();
    activity = controller.get();
  }

  @After
  public void stop() {
    controller.pause().stop().destroy();
  }

  private Object field(String name) throws Exception {
    Field f = MainActivity.class.getDeclaredField(name);
    f.setAccessible(true);
    return f.get(activity);
  }

  private void set(String name, Object value) throws Exception {
    Field f = MainActivity.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(activity, value);
  }

  private void call(String name, Class<?> type, Object value) throws Exception {
    Method m = MainActivity.class.getDeclaredMethod(name, type);
    m.setAccessible(true);
    m.invoke(activity, value);
  }

  private void call(String name) throws Exception {
    Method m = MainActivity.class.getDeclaredMethod(name);
    m.setAccessible(true);
    m.invoke(activity);
  }

  @Test
  public void successfulSaveClearsDraftAndUpdatesCounter() throws Exception {
    call("select", Domain.Sample.class, sample);
    call("key", String.class, "2");
    call("key", String.class, "1");
    assertEquals(1, field("active"));
    call("key", String.class, "5");
    assertEquals(2, field("active"));
    set("observation", "Doente");
    call("save");
    Store s = (Store) field("store");
    assertEquals(1, s.count(LocalDate.now().toString()));
    assertEquals("Doente", s.existing("123", LocalDate.now().toString()).observation());
    assertNull(field("current"));
    assertArrayEquals(new String[] {"", "", "", "", ""}, (String[]) field("values"));
    assertEquals("", field("observation"));
    assertEquals(0, field("active"));
  }

  @Test
  public void editingSavedReadingStillDoesNotDuplicate() throws Exception {
    call("select", Domain.Sample.class, sample);
    call("key", String.class, "2");
    call("save");
    call("select", Domain.Sample.class, sample);
    org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        .getButton(AlertDialog.BUTTON_POSITIVE)
        .performClick();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    call("key", String.class, "3");
    call("save");
    Store s = (Store) field("store");
    assertEquals(1, s.count(LocalDate.now().toString()));
    assertEquals(3, s.existing("123", LocalDate.now().toString()).counts()[0]);
    assertNull(field("current"));
  }

  @Test
  public void resumeOnNewDayClearsDates() throws Exception {
    prefs
        .edit()
        .putString("datesDay", LocalDate.now().minusDays(1).toString())
        .putString("harvest", "2026-10-10")
        .putString("assembly", "2026-10-20")
        .commit();
    controller.pause().resume();
    assertEquals("", prefs.getString("harvest", ""));
    assertEquals("", prefs.getString("assembly", ""));
    assertEquals("Maria", prefs.getString("user", ""));
  }

  @Test
  public void zeroConfirmationDoesNotBypassDayChange() throws Exception {
    call("select", Domain.Sample.class, sample);
    call("save");
    AlertDialog confirmation = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
    String yesterday = LocalDate.now().minusDays(1).toString();
    set("captureDay", yesterday);
    prefs.edit().putString("datesDay", yesterday).putString("harvest", yesterday).commit();
    confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertEquals(0, ((Store) field("store")).count(LocalDate.now().toString()));
    assertEquals("", prefs.getString("harvest", ""));
  }
}
