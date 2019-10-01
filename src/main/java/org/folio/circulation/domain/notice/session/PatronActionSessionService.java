package org.folio.circulation.domain.notice.session;

import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

public class PatronActionSessionService {

  public static PatronActionSessionService using(Clients clients) {
    return new PatronActionSessionService(PatronActionSessionRepository.using(clients));
  }

  private final PatronActionSessionRepository patronActionSessionRepository;

  public PatronActionSessionService(PatronActionSessionRepository patronActionSessionRepository) {
    this.patronActionSessionRepository = patronActionSessionRepository;
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> saveCheckOutSessionRecord(LoanAndRelatedRecords records) {
    UUID patronId = UUID.fromString(records.getUserId());
    UUID loanId = UUID.fromString(records.getLoan().getId());

    PatronSessionRecord patronSessionRecord =
      new PatronSessionRecord(UUID.randomUUID(),
        patronId, loanId, PatronActionType.CHECK_OUT);

    return patronActionSessionRepository.create(patronSessionRecord)
      .thenApply(mapResult(v -> records));
  }
}
