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
    Map<String, String> normalizedHeaders = new HashMap<>();

    headers.forEach((key, value) ->
      normalizedHeaders.put(key.toLowerCase(Locale.ROOT), value));

    this.headers = unmodifiableMap(normalizedHeaders);
    this.vertxContext = null;
  }

  public BulkRenewalWebContext(Map<String, String> headers, Context vertxContext) {
    Map<String, String> normalizedHeaders = new HashMap<>();

    headers.forEach((key, value) ->
      normalizedHeaders.put(key.toLowerCase(Locale.ROOT), value));

    this.headers = unmodifiableMap(normalizedHeaders);
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
}
