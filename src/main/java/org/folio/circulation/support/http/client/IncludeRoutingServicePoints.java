package org.folio.circulation.support.http.client;

public class IncludeRoutingServicePoints implements QueryParameter {

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
      consumer.consume("includeRoutingServicePoints", value.toString());
    }
  }

  @Override
  public String toString() {
    if (value == null) {
      return "No includeRoutingServicePoints";
    }

    return String.format("includeRoutingServicePoints = \"%s\"", value);
  }
}
