package org.folio.circulation.support;

public enum CqlSortDirection {

  ASC("sort.ascending"), DESC("sort.descending");

  private String representation;

  CqlSortDirection(String representation) {
    this.representation = representation;
  }

  public String asText() {
    return representation;
  }
}
