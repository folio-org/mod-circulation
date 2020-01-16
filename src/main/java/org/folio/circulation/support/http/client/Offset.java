package org.folio.circulation.support.http.client;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;

public class Offset implements QueryParameter {
  private final Integer value;

  public static Offset offset(int offset) {
    return new Offset(offset);
  }

  public static Offset noOffset() {
    return new Offset(null);
  }

  private Offset(Integer value) {
    this.value = value;
  }

  @Override
  public void writeTo(HttpRequest<Buffer> request) {
    consume(request::addQueryParam);
  }

  @Override
  public void consume(QueryStringParameterConsumer consumer) {
    if (value != null) {
      consumer.consume("offset", value.toString());
    }
  }
}
