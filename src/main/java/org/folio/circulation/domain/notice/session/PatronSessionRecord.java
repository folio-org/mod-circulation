package org.folio.circulation.domain.notice.session;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

public class PatronSessionRecord {

  private final UUID id;
  private final UUID patronId;
  private final UUID loanId;
  private final PatronActionType actionType;

  public PatronSessionRecord(UUID id, UUID patronId,
                             UUID loanId, PatronActionType actionType) {
    this.id = requireNonNull(id);
    this.patronId = requireNonNull(patronId);
    this.loanId = requireNonNull(loanId);
    this.actionType = requireNonNull(actionType);
  }

  public UUID getId() {
    return id;
  }

  public UUID getPatronId() {
    return patronId;
  }

  public UUID getLoanId() {
    return loanId;
  }

  public PatronActionType getActionType() {
    return actionType;
  }
}
