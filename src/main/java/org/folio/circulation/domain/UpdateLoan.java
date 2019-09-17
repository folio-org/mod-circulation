package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang.StringUtils;
import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeService;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class UpdateLoan {
  private final ClosedLibraryStrategyService closedLibraryStrategyService;
  private final LoanRepository loanRepository;
  private final LoanPolicyRepository loanPolicyRepository;
  private final DueDateScheduledNoticeService scheduledNoticeService;

  public UpdateLoan(Clients clients,
      LoanRepository loanRepository,
      LoanPolicyRepository loanPolicyRepository) {
    closedLibraryStrategyService = ClosedLibraryStrategyService.using(clients,
        DateTime.now(DateTimeZone.UTC), false);
    this.loanPolicyRepository = loanPolicyRepository;
    this.loanRepository = loanRepository;
    this.scheduledNoticeService = DueDateScheduledNoticeService.using(clients);
  }

  /**
   * Updates the loan due date for the loan associated with this newly created
   * recall request. No modifications are made if the request is not a recall.
   * Depending on loan/request policies, the loan date may not be updated.
   *
   * @param requestAndRelatedRecords request and related records.
   * @return the request and related records with the possibly updated loan.
   */
  CompletableFuture<Result<RequestAndRelatedRecords>> onRequestCreateOrUpdate(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();
    Loan loan = request.getLoan();

    if (request.getRequestType() == RequestType.RECALL && loan != null) {
      return loanRepository.getById(loan.getId())
          .thenComposeAsync(r -> r.after(l -> recall(l, requestAndRelatedRecords, request)));
    } else {
      return completedFuture(succeeded(requestAndRelatedRecords));
    }
  }

  private Result<LoanAndRelatedRecords> updateLoanAction(LoanAndRelatedRecords loanAndRelatedRecords, Request request) {
    Loan loan = loanAndRelatedRecords.getLoan();
    String action = request.actionOnCreateOrUpdate();

    if (StringUtils.isNotBlank(action)) {
      loan.changeAction(action);
      loan.changeItemStatus(request.getItem().getStatus().getValue());
    }

    return of(() -> loanAndRelatedRecords);
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> recall(Loan loan,
      RequestAndRelatedRecords requestAndRelatedRecords, Request request) {
    if (loan.wasDueDateChangedByRecall()) {
      // We don't need to apply the recall
      return completedFuture(succeeded(requestAndRelatedRecords));
    } else {
      return Result.of(() -> new LoanAndRelatedRecords(loan,
          requestAndRelatedRecords.getTimeZone()))
          .after(loanPolicyRepository::lookupLoanPolicy)
          .thenApply(r -> r.next(this::recall))
          .thenApply(r -> r.next(recallResult -> updateLoanAction(recallResult, request)))
          .thenComposeAsync(r -> r.after(closedLibraryStrategyService::applyClosedLibraryDueDateManagement))
          .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
          .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
          .thenApply(r -> r.map(v -> requestAndRelatedRecords.withRequest(request.withLoan(v.getLoan()))));
    }
  }

  //TODO: Possibly combine this with LoanRenewalService?
  private Result<LoanAndRelatedRecords> recall(LoanAndRelatedRecords loanAndRelatedRecords) {
    final Loan loan = loanAndRelatedRecords.getLoan();
    LoanPolicy loanPolicy = loan.getLoanPolicy();

    return loanPolicy.recall(loan)
        .map(loanAndRelatedRecords::withLoan);
  }
}
