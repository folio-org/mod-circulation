package org.folio.circulation.resources.renewal;

import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.vertx.core.Context;

public class BulkRenewalWebContext {
  private final Map<String, String> headers;
  private final Context vertxContext;

  public BulkRenewalWebContext(Map<String, String> headers) {
    this.headers = normalizeHeaders(headers);
    this.vertxContext = null;
  }

  public BulkRenewalWebContext(Map<String, String> headers, Context vertxContext) {
    this.headers = normalizeHeaders(headers);
    this.vertxContext = requireNonNull(vertxContext, "Vert.x context is required");
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public Context getVertxContext() {
    return vertxContext;
  }

  public String getUserId() {
    return headers.get("x-okapi-user-id");
  }

  private static Map<String, String> normalizeHeaders(Map<String, String> raw) {
    Map<String, String> result = new HashMap<>();
    raw.forEach((k, v) -> result.put(k.toLowerCase(Locale.ROOT), v));
    return unmodifiableMap(result);
  }
}
