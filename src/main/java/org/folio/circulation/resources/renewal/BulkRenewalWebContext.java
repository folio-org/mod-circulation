package org.folio.circulation.resources.renewal;

import static java.util.Collections.unmodifiableMap;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BulkRenewalWebContext {
  private final Map<String, String> headers;

  public BulkRenewalWebContext(Map<String, String> headers) {
    Map<String, String> normalizedHeaders = new HashMap<>();

    headers.forEach((key, value) ->
      normalizedHeaders.put(key.toLowerCase(Locale.ROOT), value));

    this.headers = unmodifiableMap(normalizedHeaders);
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public String getUserId() {
    return headers.get("x-okapi-user-id");
  }
}
