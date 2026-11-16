package dev.mgu.service;

import java.time.LocalDate;
import java.util.*;

/** Local checks only: the vehicle's existing history is not available. */
public final class RecordChecks {
  public static boolean vin(String value) {
    return value != null
        && value.matches("[A-HJ-NPR-Z0-9]{17}")
        && !value.equals("00000000000000000");
  }

  public static List<String> issues(List<ServiceRecord> records) {
    List<String> issues = new ArrayList<>();
    Set<LocalDate> dates = new HashSet<>();
    for (ServiceRecord r : records) {
      r.validate();
      if (r.date.isAfter(LocalDate.now())) issues.add(r.date + ": future service date");
      if (!dates.add(r.date)) issues.add(r.date + ": duplicate date in this list");
      for (ServiceRecord.Item i : r.items) {
        boolean known = false;
        for (int code : ServiceRecord.CODES) known |= code == i.code;
        if (!known) issues.add(r.date + ": unsupported custom item code " + i.code);
      }
    }
    return issues;
  }

  public static void requireWritable(List<ServiceRecord> records) {
    List<String> issues = issues(records);
    if (!issues.isEmpty()) throw new IllegalArgumentException(String.join("\n", issues));
  }

  public static void requireUnambiguousDates(
      List<ServiceRecord> selected, List<ServiceRecord> workspace) {
    for (ServiceRecord r : selected) {
      int count = 0;
      for (ServiceRecord other : workspace) if (r.date.equals(other.date)) count++;
      if (count > 1)
        throw new IllegalArgumentException(r.date + ": duplicate date in this workspace");
    }
  }
}
