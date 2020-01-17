package org.folio.circulation.support.http.client;

public interface QueryParameter {
  void consume(QueryStringParameterConsumer consumer);
}
