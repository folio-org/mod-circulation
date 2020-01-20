package org.folio.circulation.support.http.client;

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
  public void consume(QueryStringParameterConsumer consumer) {
    if (value != null) {
      consumer.consume("offset", value.toString());
    }
  }
}
