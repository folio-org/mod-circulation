package org.folio.circulation.support.http.client;

public class Offset implements QueryParameter {
  private final Integer value;

  public static Offset offset(int offset) {
    return new Offset(offset);
  }

  public static Offset noOffset() {
    return new Offset(null);
  }

  public static Offset zeroOffset() {
    return new Offset(0);
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

  public int getOffset() {
    return value != null ? value : 0;
  }

  public Offset nextPage(PageLimit pageLimit) {
    if (pageLimit == null || pageLimit.getLimit() == 0) {
      throw new IllegalArgumentException("Page limit must be non null and greater than 0");
    }

    return offset(getOffset() + pageLimit.getLimit());
  }
}
