package br.com.phs.coleta;

import android.content.SharedPreferences;

final class DailyDates {
  static boolean resetIfNewDay(SharedPreferences prefs, String today) {
    if (today.equals(prefs.getString("datesDay", ""))) return false;
    prefs.edit().putString("datesDay", today).remove("harvest").remove("assembly").apply();
    return true;
  }

  private DailyDates() {}
}
