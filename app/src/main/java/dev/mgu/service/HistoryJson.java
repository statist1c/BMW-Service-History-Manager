package dev.mgu.service;

import java.time.*;
import java.util.*;
import org.json.*;

final class HistoryJson {
  static long integer(JSONObject j, String key, long fallback) throws JSONException {
    if (!j.has(key)) return fallback;
    String value = String.valueOf(j.get(key));
    if (!value.matches("-?[0-9]+")) throw new IllegalArgumentException(key + " must be an integer");
    return Long.parseLong(value);
  }

  static int small(JSONObject j, String key, int fallback) throws JSONException {
    return Math.toIntExact(integer(j, key, fallback));
  }

  static JSONObject record(ServiceRecord r) throws JSONException {
    r.validate();
    JSONObject j =
        new JSONObject()
            .put("date", r.date.toString())
            .put("local_id", r.localId)
            .put("mileage_km", r.mileage)
            .put("bmw_dealer", r.bmw)
            .put("dealer_number", r.dealer)
            .put("status", r.status)
            .put("correction", r.correction);
    JSONArray a = new JSONArray();
    for (ServiceRecord.Item i : r.items)
      a.put(
          new JSONObject()
              .put("code", i.code)
              .put("status", i.status)
              .put("remaining_distance", i.distance)
              .put("remaining_time", i.months));
    return j.put("items", a);
  }

  static ServiceRecord record(JSONObject j) throws JSONException {
    ServiceRecord r = new ServiceRecord();
    String localId = j.optString("local_id", "");
    if (localId.matches(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      r.localId = UUID.fromString(localId).toString();
    r.date = LocalDate.parse(j.getString("date"));
    r.mileage = integer(j, "mileage_km", -1);
    r.bmw = j.optBoolean("bmw_dealer", false);
    r.dealer = j.optString("dealer_number", "");
    r.status = small(j, "status", 1);
    r.correction = small(j, "correction", 0);
    JSONArray a = j.getJSONArray("items");
    for (int n = 0; n < a.length(); n++) {
      JSONObject o = a.getJSONObject(n);
      ServiceRecord.Item i = new ServiceRecord.Item(small(o, "code", -1));
      i.status = small(o, "status", 1);
      i.distance = ServiceRecord.signedDistance(integer(o, "remaining_distance", 0));
      i.months = small(o, "remaining_time", 0);
      r.items.add(i);
    }
    r.validate();
    return r;
  }

  static String dump(List<ServiceRecord> records, String vin) throws JSONException {
    JSONArray a = new JSONArray();
    for (ServiceRecord r : records) a.put(record(r));
    return new JSONObject()
        .put("format", "stampbook-history")
        .put("version", 1)
        .put("saved", Instant.now().toString())
        .put("vin", vin)
        .put("records", a)
        .toString(2);
  }

  static List<ServiceRecord> parse(Object value) throws JSONException {
    if (!(value instanceof JSONObject) && !(value instanceof JSONArray))
      throw new IllegalArgumentException("Expected a history JSON object or array");
    if (value instanceof JSONObject) {
      JSONObject obj = (JSONObject) value;
      if (obj.has("version") && integer(obj, "version", 1) != 1)
        throw new IllegalArgumentException("Unsupported history file version");
    }
    JSONArray a =
        value instanceof JSONArray
            ? (JSONArray) value
            : ((JSONObject) value).getJSONArray("records");
    if (a.length() > 2000) throw new IllegalArgumentException("Maximum 2000 records");
    List<ServiceRecord> rs = new ArrayList<>();
    for (int n = 0; n < a.length(); n++) rs.add(record(a.getJSONObject(n)));
    return rs;
  }
}
