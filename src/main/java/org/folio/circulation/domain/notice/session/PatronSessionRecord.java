package org.folio.circulation.domain.notice.session;

import static java.util.Objects.requireNonNull;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ACTION_TYPE;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.LOAN_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.PATRON_ID;
import static org.folio.circulation.domain.notice.session.PatronActionSessionProperties.SESSION_ID;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;

import java.util.UUID;

import org.folio.circulation.domain.Loan;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
public class PatronSessionRecord {

  private final UUID id;
  private final UUID patronId;
  private final UUID loanId;
  private final UUID sessionId;
  private final PatronActionType actionType;
  @JsonIgnore
  @ToString.Exclude
  private final Loan loan;

  public PatronSessionRecord(UUID id, UUID patronId, UUID loanId, PatronActionType actionType) {
    this(id, patronId, loanId, null, actionType, null);
  }

  public PatronSessionRecord(UUID id, UUID patronId, UUID loanId, UUID sessionId,
    PatronActionType actionType) {

    this(id, patronId, loanId, sessionId, actionType, null);
  }

  public PatronSessionRecord(UUID id, UUID patronId, UUID loanId, UUID sessionId,
    PatronActionType actionType, Loan loan) {

    this.id = requireNonNull(id);
    this.patronId = requireNonNull(patronId);
    this.loanId = requireNonNull(loanId);
    this.sessionId = sessionId;
    this.actionType = requireNonNull(actionType);
    this.loan = loan;
  }

  public PatronSessionRecord withLoan(Loan newLoan) {
    return new PatronSessionRecord(id, patronId, loanId, sessionId, actionType, newLoan);
  }

  public static PatronSessionRecord from(JsonObject representation) {
    UUID id = getUUIDProperty(representation, ID);
    UUID patronId = getUUIDProperty(representation, PATRON_ID);
    UUID loanId = getUUIDProperty(representation, LOAN_ID);
    UUID sessionId = getUUIDProperty(representation, SESSION_ID);
    String actionTypeValue = getProperty(representation, ACTION_TYPE);

    return PatronActionType.from(actionTypeValue)
      .map(patronActionType -> new PatronSessionRecord(id, patronId, loanId, sessionId, patronActionType))
      .orElse(null);
  }
}
