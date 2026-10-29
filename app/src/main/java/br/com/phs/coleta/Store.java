package br.com.phs.coleta;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
import org.json.*;

final class Store extends SQLiteOpenHelper {
  Store(Context context) {
    super(context, "phs.db", null, 1);
    setWriteAheadLoggingEnabled(true);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(
        "CREATE TABLE samples(feid TEXT PRIMARY KEY,trial TEXT,location TEXT,variety TEXT,parcel"
            + " TEXT,repetition TEXT)");
    db.execSQL("CREATE INDEX samples_parcel ON samples(parcel)");
    db.execSQL(
        "CREATE TABLE readings(feid TEXT NOT NULL,day TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY"
            + " KEY(feid,day))");
    db.execSQL("CREATE TABLE users(name TEXT PRIMARY KEY)");
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    throw new IllegalStateException("Migração de banco não definida.");
  }

  void replaceCatalog(List<Domain.Sample> samples) {
    SQLiteDatabase db = getWritableDatabase();
    db.beginTransaction();
    try {
      db.delete("samples", null, null);
      for (Domain.Sample s : samples) {
        ContentValues v = new ContentValues();
        String[] cols = {"feid", "trial", "location", "variety", "parcel", "repetition"};
        String[] fields = s.fields();
        for (int i = 0; i < 6; i++) v.put(cols[i], fields[i]);
        db.insertOrThrow("samples", null, v);
      }
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  Domain.Sample find(String feid) {
    List<Domain.Sample> samples = query("feid", feid);
    return samples.isEmpty() ? null : samples.get(0);
  }

  List<Domain.Sample> byParcel(String parcel) {
    return query("parcel", parcel);
  }

  private List<Domain.Sample> query(String col, String value) {
    List<Domain.Sample> list = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query("samples", null, col + "=?", new String[] {value}, null, null, "feid")) {
      while (c.moveToNext())
        list.add(
            new Domain.Sample(
                c.getString(0),
                c.getString(1),
                c.getString(2),
                c.getString(3),
                c.getString(4),
                c.getString(5)));
    }
    return list;
  }

  int catalogCount() {
    try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM samples", null)) {
      c.moveToFirst();
      return c.getInt(0);
    }
  }

  List<String> users() {
    List<String> list = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT name FROM users ORDER BY name COLLATE NOCASE", null)) {
      while (c.moveToNext()) list.add(c.getString(0));
    }
    return list;
  }

  boolean addUser(String name) {
    ContentValues v = new ContentValues();
    v.put("name", name.trim());
    return getWritableDatabase()
            .insertWithOnConflict("users", null, v, SQLiteDatabase.CONFLICT_IGNORE)
        >= 0;
  }

  boolean renameUser(String old, String name) {
    ContentValues v = new ContentValues();
    v.put("name", name);
    try {
      return getWritableDatabase().update("users", v, "name=?", new String[] {old}) > 0;
    } catch (SQLiteConstraintException ex) {
      return false;
    }
  }

  void deleteUser(String name) {
    getWritableDatabase().delete("users", "name=?", new String[] {name});
  }

  void save(Domain.Reading reading) {
    ContentValues v = new ContentValues();
    v.put("feid", reading.sample().feid());
    v.put("day", reading.day());
    v.put("payload", encode(reading));
    getWritableDatabase()
        .insertWithOnConflict("readings", null, v, SQLiteDatabase.CONFLICT_REPLACE);
  }

  Domain.Reading existing(String feid, String day) {
    try (Cursor c =
        getReadableDatabase()
            .query(
                "readings",
                new String[] {"payload"},
                "feid=? AND day=?",
                new String[] {feid, day},
                null,
                null,
                null)) {
      return c.moveToFirst() ? decode(c.getString(0)) : null;
    }
  }

  List<Domain.Reading> readings(String day) {
    List<Domain.Reading> list = new ArrayList<>();
    try (Cursor c =
        getReadableDatabase()
            .query(
                "readings",
                new String[] {"payload"},
                "day=?",
                new String[] {day},
                null,
                null,
                "feid")) {
      while (c.moveToNext()) list.add(decode(c.getString(0)));
    }
    return list;
  }

  LinkedHashMap<String, Integer> days() {
    LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT day,COUNT(*) FROM readings GROUP BY day ORDER BY day DESC", null)) {
      while (c.moveToNext()) map.put(c.getString(0), c.getInt(1));
    }
    return map;
  }

  int count(String day) {
    try (Cursor c =
        getReadableDatabase()
            .rawQuery("SELECT COUNT(*) FROM readings WHERE day=?", new String[] {day})) {
      c.moveToFirst();
      return c.getInt(0);
    }
  }

  void clearReadings() {
    getWritableDatabase().delete("readings", null, null);
  }

  static String encode(Domain.Reading r) {
    try {
      JSONObject o = new JSONObject();
      o.put("sample", new JSONArray(Arrays.asList(r.sample().fields())));
      o.put("day", r.day());
      o.put("timestamp", r.timestamp());
      o.put("harvest", r.harvest());
      o.put("assembly", r.assembly());
      o.put("observation", r.observation());
      JSONArray counts = new JSONArray();
      for (int n : r.counts()) counts.put(n);
      o.put("counts", counts);
      o.put("user", r.user());
      o.put("deviceId", r.deviceId());
      o.put("deviceName", r.deviceName());
      return o.toString();
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  static Domain.Reading decode(String text) {
    try {
      JSONObject o = new JSONObject(text);
      JSONArray s = o.getJSONArray("sample"), a = o.getJSONArray("counts");
      int[] counts = new int[5];
      for (int i = 0; i < 5; i++) counts[i] = a.getInt(i);
      return new Domain.Reading(
          new Domain.Sample(
              s.getString(0),
              s.getString(1),
              s.getString(2),
              s.getString(3),
              s.getString(4),
              s.getString(5)),
          o.getString("day"),
          o.getString("timestamp"),
          o.getString("harvest"),
          o.getString("assembly"),
          o.getString("observation"),
          counts,
          o.getString("user"),
          o.getString("deviceId"),
          o.getString("deviceName"));
    } catch (JSONException e) {
      throw new IllegalStateException("Registro inválido no banco local", e);
    }
  }
}
