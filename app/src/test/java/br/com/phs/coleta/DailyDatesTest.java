package br.com.phs.coleta;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class DailyDatesTest {
  private SharedPreferences settings() {
    SharedPreferences p =
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("daily-test", Context.MODE_PRIVATE);
    p.edit().clear().commit();
    return p;
  }

  @Test
  public void sameDayPreservesDates() {
    SharedPreferences p = settings();
    p.edit()
        .putString("datesDay", "2026-10-30")
        .putString("harvest", "2026-10-10")
        .putString("assembly", "2026-10-20")
        .commit();
    assertFalse(DailyDates.resetIfNewDay(p, "2026-10-30"));
    assertEquals("2026-10-10", p.getString("harvest", ""));
  }

  @Test
  public void newDayClearsOnlyDates() {
    SharedPreferences p = settings();
    p.edit()
        .putString("datesDay", "2026-10-29")
        .putString("harvest", "2026-10-10")
        .putString("assembly", "2026-10-20")
        .putString("user", "Maria")
        .putString("deviceName", "CAMPO01")
        .commit();
    assertTrue(DailyDates.resetIfNewDay(p, "2026-10-30"));
    assertEquals("", p.getString("harvest", ""));
    assertEquals("", p.getString("assembly", ""));
    assertEquals("Maria", p.getString("user", ""));
    assertEquals("CAMPO01", p.getString("deviceName", ""));
  }

  @Test
  public void upgradeDoesNotReuseUndatedPreferences() {
    SharedPreferences p = settings();
    p.edit().putString("harvest", "2026-10-10").commit();
    assertTrue(DailyDates.resetIfNewDay(p, "2026-10-30"));
    assertFalse(p.contains("harvest"));
  }
}
