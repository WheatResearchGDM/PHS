package br.com.phs.coleta;

import static org.junit.Assert.*;

import org.junit.Test;

public class EntryRulesTest {
  @Test
  public void singleDigitsAdvanceExceptOne() {
    for (int n = 0; n <= 9; n++) {
      EntryRules.Result r = EntryRules.digit("", String.valueOf(n));
      assertTrue(r.accepted());
      assertEquals(n != 1, r.advance());
      assertEquals(String.valueOf(n), r.value());
    }
  }

  @Test
  public void tenThroughFifteenCompleteAndAdvance() {
    for (int n = 0; n <= 5; n++) {
      EntryRules.Result r = EntryRules.digit("1", String.valueOf(n));
      assertTrue(r.accepted());
      assertTrue(r.advance());
      assertEquals("1" + n, r.value());
    }
  }

  @Test
  public void sixteenThroughNineteenAreRejected() {
    for (int n = 6; n <= 9; n++) {
      EntryRules.Result r = EntryRules.digit("1", String.valueOf(n));
      assertFalse(r.accepted());
      assertEquals("1", r.value());
      assertFalse(r.advance());
    }
  }

  @Test
  public void selectingCompletedFieldStartsNewValue() {
    assertEquals("3", EntryRules.digit("15", "3").value());
    assertTrue(EntryRules.digit("15", "3").advance());
  }

  @Test
  public void newReadingsAreLimitedToFifteen() {
    assertTrue(EntryRules.validNewCounts(new int[] {0, 1, 10, 11, 15}));
    assertFalse(EntryRules.validNewCounts(new int[] {16, 0, 0, 0, 0}));
  }
}
