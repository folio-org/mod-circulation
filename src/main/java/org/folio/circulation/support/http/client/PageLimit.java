package org.folio.circulation.support.http.client;

import static java.lang.Integer.MAX_VALUE;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;

public class PageLimit implements QueryParameter {
  private final Integer value;

  public static PageLimit limit(int limit) {
    return new PageLimit(limit);
  }

  public static PageLimit maximumLimit() {
    return limit(MAX_VALUE);
  }

  public static PageLimit noLimit() {
    return new PageLimit(null);
  }

  public static PageLimit one() {
    return limit(1);
  }

  public static PageLimit oneThousand() {
    return limit(1000);
  }

  private PageLimit(Integer value) {
    this.value = value;
  }

  @Override
  public void writeTo(HttpRequest<Buffer> request) {
    //TODO: Replace with null value pattern
    if (value != null) {
      request.addQueryParam("limit", value.toString());
    }
  }
}
