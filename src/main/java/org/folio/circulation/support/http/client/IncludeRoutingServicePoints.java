package org.folio.circulation.support.http.client;

import static java.lang.String.format;

public class IncludeRoutingServicePoints implements QueryParameter {

  private static final String PARAM_NAME = "includeRoutingServicePoints";
  private final Boolean value;

  public static IncludeRoutingServicePoints enabled() {
    return new IncludeRoutingServicePoints(true);
  }

  private IncludeRoutingServicePoints(Boolean value) {
    this.value = value;
  }

  @Override
  public void consume(QueryStringParameterConsumer consumer) {
    if (value != null) {
      consumer.consume(PARAM_NAME, value.toString());
    }
  }

  @Override
  public String toString() {
    if (value == null) {
      return format("No %s", PARAM_NAME);
    }

    return format("%s = \"%s\"", PARAM_NAME, value);
  }
}
