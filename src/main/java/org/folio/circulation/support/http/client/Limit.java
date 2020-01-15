package org.folio.circulation.support.http.client;

import static java.lang.Integer.MAX_VALUE;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;

public class Limit implements QueryParameter {
  private final Integer value;

  public static Limit limit(int limit) {
    return new Limit(limit);
  }

  public static Limit maximumLimit() {
    return limit(MAX_VALUE);
  }

  public static Limit noLimit() {
    return new Limit(null);
  }

  public static Limit one() {
    return limit(1);
  }

  public static Limit oneThousand() {
    return limit(1000);
  }

  private Limit(Integer value) {
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
