package org.folio.circulation.domain.notice.session;

public class ExpiredSession {
  private String patronId;
  private PatronActionType actionType;

  public ExpiredSession() {
  }

  public ExpiredSession(String patronId, PatronActionType actionType) {
    this.patronId = patronId;
    this.actionType = actionType;
  }

  public String getPatronId() {
    return patronId;
  }

  public PatronActionType getActionType() {
    return actionType;
  }
}
