package org.folio.circulation.support;

public enum CqlSortDirection {

  ASCENDING("sort.ascending"), DESCENDING("sort.descending");

  private String representation;

  CqlSortDirection(String representation) {
    this.representation = representation;
  }

  public String asText() {
    return representation;
  }
}
