package br.com.phs.coleta;

import java.util.List;

/** Suppresses the tag just saved until it has left the frame, or a different tag appears. */
final class ScanGate {
  private String blocked;
  private long absentSince = -1;

  synchronized void afterSave(String code) {
    blocked = code;
    absentSince = -1;
  }

  synchronized void clear() {
    blocked = null;
    absentSince = -1;
  }

  synchronized String accept(List<String> codes, long now) {
    if (blocked != null) {
      if (codes.contains(blocked)) absentSince = -1;
      else {
        if (absentSince < 0) absentSince = now;
        if (now - absentSince >= 800) clear();
      }
    }
    for (String code : codes) if (!code.equals(blocked)) return code;
    return null;
  }
}
