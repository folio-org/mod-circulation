package org.folio.circulation.support;

public class CqlQuery {
  private final String query;

  public CqlQuery(String query) {
    this.query = query;
  }

  Result<String> encode() {
    return CqlHelper.encodeQuery(query);
  }
}
