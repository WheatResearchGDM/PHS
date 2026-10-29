package br.com.phs.coleta;

import java.util.Locale;

public final class Domain {
  public static final String[] INPUT = {
    "FEID", "ENSAIO", "LOCAL", "VARIEDADE", "PARCELA", "REPETIÇÃO"
  };
  public static final String[] OUTPUT = {
    "FEID",
    "ENSAIO",
    "LOCAL",
    "VARIEDADE",
    "PARCELA",
    "REPETIÇÃO",
    "COLHEITA",
    "MONTAGEM",
    "LEITURA",
    "OBSERVAÇÃO",
    "R",
    "MR",
    "MS",
    "S",
    "AS",
    "NºESPIGAS",
    "MÉDIA",
    "DATA DE COLETA",
    "USUÁRIO",
    "DEVICE ID"
  };

  public record Sample(
      String feid,
      String trial,
      String location,
      String variety,
      String parcel,
      String repetition) {
    public String[] fields() {
      return new String[] {feid, trial, location, variety, parcel, repetition};
    }
  }

  public record Reading(
      Sample sample,
      String day,
      String timestamp,
      String harvest,
      String assembly,
      String observation,
      int[] counts,
      String user,
      String deviceId,
      String deviceName) {}

  public static int count(String value) {
    if (value == null || value.trim().isEmpty()) return 0;
    if (!value.matches("[0-9]{1,2}"))
      throw new IllegalArgumentException("Use de 0 a 99 espigas por categoria.");
    return Integer.parseInt(value);
  }

  public static int total(int[] counts) {
    int sum = 0;
    for (int n : counts) {
      if (n < 0 || n > 99) throw new IllegalArgumentException("Contagem inválida");
      sum += n;
    }
    return sum;
  }

  public static Double mean(int[] counts) {
    int sum = total(counts);
    if (sum == 0) return null;
    double weighted = 0;
    for (int i = 0; i < 5; i++) weighted += counts[i] * (2 * i + 1);
    return weighted / sum;
  }

  public static String cleanId(String value) {
    String text = value == null ? "" : value.trim();
    return text.matches("[0-9]+\\.0+") ? text.substring(0, text.indexOf('.')) : text;
  }

  public static String fileName(String day, String device, String extension) {
    String[] p = day.split("-");
    String safe = device.trim().replaceAll("[^\\p{L}\\p{N}_-]", "_");
    if (safe.trim().isEmpty()) safe = "DISPOSITIVO";
    return "RESULTADO_PHS_"
        + p[2]
        + "-"
        + p[1]
        + "-"
        + p[0]
        + "_"
        + safe
        + "."
        + extension.toLowerCase(Locale.ROOT);
  }

  private Domain() {}
}
