package br.com.phs.coleta;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.Test;

public class SheetIOTest {
  private Domain.Reading reading() {
    return new Domain.Reading(
        new Domain.Sample("0012", "TESTE", "IJUÍ", "VARIEDADE_TESTE", "0034", "2"),
        "2026-10-29",
        "2026-10-29T10:15:30",
        "2026-10-15",
        "",
        "Germinado; teste\ncom quebra",
        new int[] {2, 2, 2, 2, 2},
        "Coletor Teste",
        "test-installation-id",
        "CAMPO01");
  }

  @Test
  public void excelRoundTripPreservesIdentifiersAndAccents() throws Exception {
    File file = File.createTempFile("phs-roundtrip-", ".xlsx");
    try {
      try (OutputStream out = new FileOutputStream(file)) {
        SheetIO.write(out, List.of(reading()), false);
      }
      List<Domain.Sample> samples = SheetIO.read(file, false);
      assertEquals(1, samples.size());
      assertEquals(reading().sample(), samples.get(0));
      try (ZipFile zip = new ZipFile(file)) {
        String xml =
            new String(
                zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(xml.contains("<f>IF(P2=0"));
        assertTrue(xml.contains("USUÁRIO"));
        assertTrue(xml.contains("<v>5.0</v>"));
      }
    } finally {
      file.delete();
    }
  }

  @Test
  public void csvRoundTripHandlesNewlinesAndQuotes() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    SheetIO.write(bytes, List.of(reading()), true);
    List<List<String>> rows = SheetIO.parseCsv(bytes.toString(StandardCharsets.UTF_8), ';');
    assertEquals(2, rows.size());
    assertEquals(20, rows.get(1).size());
    assertEquals("Germinado; teste\ncom quebra", rows.get(1).get(9));
    assertEquals("0012", rows.get(1).get(0));
    assertEquals("", rows.get(1).get(7));
  }

  @Test
  public void duplicateFeidIsRejectedBeforeImport() throws Exception {
    File f = File.createTempFile("phs-duplicate-", ".csv");
    try {
      Files.write(
          f.toPath(),
          "FEID;ENSAIO;LOCAL;VARIEDADE;PARCELA;REPETIÇÃO\n1;T;L;V;2;1\n1;T;L;V;3;1\n"
              .getBytes(StandardCharsets.UTF_8));
      assertThrows(IOException.class, () -> SheetIO.read(f, true));
    } finally {
      f.delete();
    }
  }

  @Test
  public void blankFeidIsRejected() throws Exception {
    File f = File.createTempFile("phs-blank-", ".csv");
    try {
      Files.write(
          f.toPath(),
          "FEID;ENSAIO;LOCAL;VARIEDADE;PARCELA;REPETIÇÃO\n;T;L;V;2;1\n"
              .getBytes(StandardCharsets.UTF_8));
      assertThrows(IOException.class, () -> SheetIO.read(f, true));
    } finally {
      f.delete();
    }
  }

  @Test
  public void malformedCsvIsRejected() {
    assertThrows(IOException.class, () -> SheetIO.parseCsv("a;\"never closed", ';'));
  }

  @Test
  public void writesIndependentVerificationFixture() throws Exception {
    File out = new File("build/test-output/verified-output.xlsx");
    out.getParentFile().mkdirs();
    try (OutputStream stream = new FileOutputStream(out)) {
      SheetIO.write(stream, List.of(reading()), false);
    }
  }
}
