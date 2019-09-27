package org.folio.circulation.domain.notice.session;

import static java.util.Objects.requireNonNull;

public class PatronSessionRecord {

  private final String id;
  private final String patronId;
  private final String loanId;
  private final PatronActionType actionType;

  public PatronSessionRecord(String id, String patronId,
                             String loanId, PatronActionType actionType) {
    this.id = requireNonNull(id);
    this.patronId = requireNonNull(patronId);
    this.loanId = requireNonNull(loanId);
    this.actionType = requireNonNull(actionType);
  }

  public String getId() {
    return id;
  }

  public String getPatronId() {
    return patronId;
  }

  public String getLoanId() {
    return loanId;
  }

  public PatronActionType getActionType() {
    return actionType;
  }
}
