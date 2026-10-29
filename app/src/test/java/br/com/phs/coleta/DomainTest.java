package br.com.phs.coleta;

import static org.junit.Assert.*;

import org.junit.Test;

public class DomainTest {
  @Test
  public void blanksAndZeroAreEquivalent() {
    assertEquals(0, Domain.count(""));
    assertEquals(0, Domain.count(null));
    assertEquals(0, Domain.count("0"));
    assertEquals(0, Domain.count("00"));
  }

  @Test
  public void acceptsTwoDigits() {
    assertEquals(99, Domain.count("99"));
    assertEquals(5, Domain.count("05"));
  }

  @Test
  public void rejectsThreeDigits() {
    assertThrows(IllegalArgumentException.class, () -> Domain.count("100"));
  }

  @Test
  public void rejectsNegativeAndDecimalCounts() {
    assertThrows(IllegalArgumentException.class, () -> Domain.count("-1"));
    assertThrows(IllegalArgumentException.class, () -> Domain.count("1.5"));
  }

  @Test
  public void weightedMeanMatchesTemplate() {
    assertEquals(10, Domain.total(new int[] {2, 2, 2, 2, 2}));
    assertEquals(5.0, Domain.mean(new int[] {2, 2, 2, 2, 2}), 0.00001);
    assertEquals(9.0, Domain.mean(new int[] {0, 0, 0, 0, 10}), 0.00001);
  }

  @Test
  public void zeroTotalHasNoMean() {
    assertNull(Domain.mean(new int[] {0, 0, 0, 0, 0}));
  }

  @Test
  public void outputNameUsesDayMonthYear() {
    assertEquals(
        "RESULTADO_PHS_29-10-2026_CAMPO01.xlsx", Domain.fileName("2026-10-29", "CAMPO01", "xlsx"));
  }

  @Test
  public void normalizesOnlyNumericSuffix() {
    assertEquals("628244", Domain.cleanId("628244.0"));
    assertEquals("0012", Domain.cleanId("0012"));
    assertEquals("A.0", Domain.cleanId("A.0"));
  }
}
