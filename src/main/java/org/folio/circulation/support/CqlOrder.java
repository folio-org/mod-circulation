package org.folio.circulation.support;

public class CqlOrder {

  private final String index;
  private final CqlSortDirection direction;

  public CqlOrder(String index, CqlSortDirection direction) {
    this.index = index;
    this.direction = direction;
  }

  public static CqlOrder asc(String index) {
    return new CqlOrder(index, CqlSortDirection.ASC);
  }

  public static CqlOrder desc(String index) {
    return new CqlOrder(index, CqlSortDirection.DESC);
  }

  public String asText() {
    return String.format("%s/%s", index, direction.asText());
  }
}
