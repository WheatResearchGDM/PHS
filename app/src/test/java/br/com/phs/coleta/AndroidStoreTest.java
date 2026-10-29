package br.com.phs.coleta;

import static org.junit.Assert.*;

import android.content.Context;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AndroidStoreTest {
  private Store store;
  private final Domain.Sample sample =
      new Domain.Sample("123", "TESTE", "LOCAL", "VARIEDADE", "42", "1");

  @Before
  public void createDatabase() {
    Context context = RuntimeEnvironment.getApplication();
    context.deleteDatabase("phs.db");
    store = new Store(context);
    store.replaceCatalog(List.of(sample));
  }

  @After
  public void closeDatabase() {
    store.close();
  }

  private Domain.Reading reading(String day, int value) {
    return new Domain.Reading(
        sample,
        day,
        day + "T10:30:00",
        "",
        "",
        "Germinado",
        new int[] {value, 0, 0, 0, 0},
        "Maria",
        "test-id",
        "CAMPO01");
  }

  @Test
  public void editingSameFeidAndDayDoesNotDuplicate() {
    store.save(reading("2026-10-29", 2));
    store.save(reading("2026-10-29", 7));
    assertEquals(1, store.count("2026-10-29"));
    assertEquals(7, store.existing("123", "2026-10-29").counts()[0]);
    store.save(reading("2026-10-30", 3));
    assertEquals(2, store.days().size());
  }

  @Test
  public void catalogRefreshPreservesReadingSnapshot() {
    store.save(reading("2026-10-29", 2));
    store.replaceCatalog(
        List.of(new Domain.Sample("123", "NOVO", "LOCAL", "RENOMEADA", "43", "1")));
    assertEquals("RENOMEADA", store.find("123").variety());
    assertEquals("VARIEDADE", store.existing("123", "2026-10-29").sample().variety());
  }

  @Test
  public void clearingReadingsPreservesUsersAndCatalog() {
    store.addUser("Maria");
    store.save(reading("2026-10-29", 2));
    store.clearReadings();
    assertTrue(store.days().isEmpty());
    assertEquals(List.of("Maria"), store.users());
    assertEquals(1, store.catalogCount());
  }

  @Test
  public void deletingUserPreservesHistoricalCollector() {
    store.addUser("Maria");
    store.save(reading("2026-10-29", 2));
    store.deleteUser("Maria");
    assertTrue(store.users().isEmpty());
    assertEquals("Maria", store.existing("123", "2026-10-29").user());
  }
}
