package org.folio.circulation.support;

public class CqlSortClause {

  private final String index;
  private final CqlSortDirection direction;

  public CqlSortClause(String index, CqlSortDirection direction) {
    this.index = index;
    this.direction = direction;
  }

  public static CqlSortClause ascending(String index) {
    return new CqlSortClause(index, CqlSortDirection.ASCENDING);
  }

  public static CqlSortClause descending(String index) {
    return new CqlSortClause(index, CqlSortDirection.DESCENDING);
  }

  public String asText() {
    return String.format("%s/%s", index, direction.asText());
  }
}
