package org.folio.circulation.domain;

public class AccountFeeFineOwnerInfo {
  private final String ownerId;
  private final String owner;

  public AccountFeeFineOwnerInfo(String ownerId, String feeFineOwner) {
    this.ownerId = ownerId;
    this.owner = feeFineOwner;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public String getOwner() {
    return owner;
  }
}
