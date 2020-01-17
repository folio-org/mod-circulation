package org.folio.circulation.support.http.client;

import static java.lang.Integer.MAX_VALUE;

public class PageLimit implements QueryParameter {
  private static final PageLimit MAXIMUM_PAGE_LIMIT = limit(MAX_VALUE);
  private static final PageLimit NO_PAGE_LIMIT = new PageLimit(null);
  private static final PageLimit ONE = limit(1);
  private static final PageLimit ONE_THOUSAND = limit(1000);

  private final Integer value;

  public static PageLimit limit(int limit) {
    return new PageLimit(limit);
  }

  public static PageLimit maximumLimit() {
    return MAXIMUM_PAGE_LIMIT;
  }

  public static PageLimit noLimit() {
    return NO_PAGE_LIMIT;
  }

  public static PageLimit one() {
    return ONE;
  }

  public static PageLimit oneThousand() {
    return ONE_THOUSAND;
  }

  private PageLimit(Integer value) {
    this.value = value;
  }

  @Override
  public void consume(QueryStringParameterConsumer consumer) {
    if (value != null) {
      consumer.consume("limit", value.toString());
    }
  }
}
