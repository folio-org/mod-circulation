package org.folio.circulation.domain;

public class AccountFeeFineTypeInfo {
  private final String feeFineId;
  private final String feeFineType;

  public AccountFeeFineTypeInfo(String feeFineId, String feeFineType) {
    this.feeFineId = feeFineId;
    this.feeFineType = feeFineType;
  }

  public String getFeeFineId() {
    return feeFineId;
  }

  public String getFeeFineType() {
    return feeFineType;
  }
}
