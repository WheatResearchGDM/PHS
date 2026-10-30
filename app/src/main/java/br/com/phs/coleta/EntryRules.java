package br.com.phs.coleta;

/** Keypad rules for new readings; historical records keep their original values. */
final class EntryRules {
  record Result(String value, boolean advance, boolean accepted) {}

  static Result digit(String current, String digit) {
    if (!digit.matches("[0-9]")) return new Result(current, false, false);
    String candidate = current.equals("1") ? current + digit : digit;
    if (Integer.parseInt(candidate) > 15) return new Result(current, false, false);
    return new Result(candidate, !candidate.equals("1"), true);
  }

  static boolean validNewCounts(int[] counts) {
    for (int n : counts) if (n < 0 || n > 15) return false;
    return true;
  }

  private EntryRules() {}
}
