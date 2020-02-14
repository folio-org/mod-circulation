package org.folio.circulation.domain;

public class AccountFeeFineOwnerAndTypeInfo {
  private final String ownerId;
  private final String owner;
  private final String feeFineId;
  private final String feeFineType;

  public AccountFeeFineOwnerAndTypeInfo(String ownerId, String feeFineOwner, String feeFineId,
    String feeFineType) {
    this.ownerId = ownerId;
    this.owner = feeFineOwner;
    this.feeFineId = feeFineId;
    this.feeFineType = feeFineType;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public String getOwner() {
    return owner;
  }

  public String getFeeFineId() {
    return feeFineId;
  }

  public String getFeeFineType() {
    return feeFineType;
  }
}
