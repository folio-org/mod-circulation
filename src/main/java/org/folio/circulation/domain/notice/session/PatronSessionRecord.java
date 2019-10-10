package org.folio.circulation.domain.notice.session;

import static java.util.Objects.requireNonNull;

import java.util.UUID;

import org.folio.circulation.domain.Loan;

public class PatronSessionRecord {

  private final UUID id;
  private final UUID patronId;
  private final UUID loanId;
  private final PatronActionType actionType;

  private final Loan loan;

  public PatronSessionRecord(UUID id, UUID patronId,
                             UUID loanId, PatronActionType actionType) {
    this(id, patronId, loanId, actionType, null);
  }

  public PatronSessionRecord(UUID id, UUID patronId,
                             UUID loanId, PatronActionType actionType,
                             Loan loan) {
    this.id = requireNonNull(id);
    this.patronId = requireNonNull(patronId);
    this.loanId = requireNonNull(loanId);
    this.actionType = requireNonNull(actionType);

    this.loan = loan;
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

  public Loan getLoan() {
    return loan;
  }

  public PatronSessionRecord withLoan(Loan newLoan) {
    return new PatronSessionRecord(id, patronId, loanId, actionType, newLoan);
  }
}
