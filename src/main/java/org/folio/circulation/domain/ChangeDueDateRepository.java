package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangeDueDateRepository {

  private static final Logger log = LoggerFactory
    .getLogger(MethodHandles.lookup().lookupClass());

  private final LoanRepository loanRepository;

  public ChangeDueDateRepository(Clients clients) {
    loanRepository = new LoanRepository(clients);
  }

  public CompletableFuture<Result<Loan>> changeDueDate(String loanId,
    DateTime dueDate) {

    log.info("Changing due date for {} to {}", loanId, dueDate);
    return loanRepository.getById(loanId)
      .thenApply(r -> r.map(r1 -> r1.changeDueDate(dueDate)))
      .thenApply(r-> r.map(loan -> {
        loan.changeAction(LoanAction.DUE_DATE_CHANGE);
        return loan;
      }))
      .thenCompose(r -> r.after(loanRepository::replaceLoan))
      .thenApply(r-> r.map(LoanAndRelatedRecords::getLoan));
  }
}
