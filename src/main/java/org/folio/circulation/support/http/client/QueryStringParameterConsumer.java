package org.folio.circulation.support.http.client;

@FunctionalInterface
public interface QueryStringParameterConsumer {
  void consume(String name, String value);
}
