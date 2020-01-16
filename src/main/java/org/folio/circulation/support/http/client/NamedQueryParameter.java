package org.folio.circulation.support.http.client;

public class NamedQueryParameter implements QueryParameter {
  private final String name;
  private final String value;

  public static NamedQueryParameter namedParameter(String name, String value) {
    return new NamedQueryParameter(name, value);
  }

  private NamedQueryParameter(String name, String value) {
    this.name = name;
    this.value = value;
  }

  @Override
  public void consume(QueryStringParameterConsumer consumer) {
    consumer.consume(name, value);
  }
}
