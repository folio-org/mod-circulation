package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.notice.NoticeContextUtil;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class UpdateLoan {
  private final ClosedLibraryStrategyService closedLibraryStrategyService;
  private final LoanRepository loanRepository;
  private final LoanPolicyRepository loanPolicyRepository;
  private final PatronNoticeService patronNoticeService;

  public UpdateLoan(Clients clients,
      LoanRepository loanRepository,
      LoanPolicyRepository loanPolicyRepository) {
    closedLibraryStrategyService = ClosedLibraryStrategyService.using(clients,
        DateTime.now(DateTimeZone.UTC), false);
    this.loanPolicyRepository = loanPolicyRepository;
    this.loanRepository = loanRepository;
    this.patronNoticeService = new PatronNoticeService(
      new PatronNoticePolicyRepository(clients), clients);
  }

  /**
   * Updates the loan due date for the loan associated with this newly created
   * recall request. No modifications are made if the request is not a recall.
   * Depending on loan/request policies, the loan date may not be updated.
   * 
   * @param requestAndRelatedRecords request and related records. 
   * @return the request and related records with the possibly updated loan.
   */
  CompletableFuture<Result<RequestAndRelatedRecords>> onRequestCreation(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    Request request = requestAndRelatedRecords.getRequest();
    Loan loan = request.getLoan();
    if (request.getRequestType() == RequestType.RECALL && loan != null) {
      DateTime dueDateBeforeRecall = loan.getDueDate();
      return loanRepository.getById(loan.getId())
          .thenApply(r -> r.map(LoanAndRelatedRecords::new))
          .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
          .thenApply(r -> r.next(this::recall))
          .thenComposeAsync(r -> r.after(closedLibraryStrategyService::applyClosedLibraryDueDateManagement))
          .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
          .thenApply(r -> r.next(records -> sendRecallNotice(records, dueDateBeforeRecall)))
          .thenApply(r -> r.map(v -> requestAndRelatedRecords));
    } else {
      return completedFuture(succeeded(requestAndRelatedRecords));
    }
  }

  private Result<LoanAndRelatedRecords> recall(LoanAndRelatedRecords loanAndRelatedRecords) {
    LoanPolicy loanPolicy = loanAndRelatedRecords.getLoanPolicy();
    return loanPolicy.recall(loanAndRelatedRecords.getLoan())
        .map(loanAndRelatedRecords::withLoan);
  }

  private Result<LoanAndRelatedRecords> sendRecallNotice(
    LoanAndRelatedRecords loanAndRelatedRecords, DateTime dueDateBeforeRecall) {

    Loan loan = loanAndRelatedRecords.getLoan();
    if (!loan.getDueDate().equals(dueDateBeforeRecall)) {
      PatronNoticeEvent itemRecalledEvent = new PatronNoticeEventBuilder()
        .withItem(loan.getItem())
        .withUser(loan.getUser())
        .withEventType(NoticeEventType.RECALL_TO_LOANEE)
        .withTiming(NoticeTiming.UPON_AT)
        .withNoticeContext(NoticeContextUtil.createLoanNoticeContext(loan))
        .build();
      patronNoticeService.acceptNoticeEvent(itemRecalledEvent);
    }
    return succeeded(loanAndRelatedRecords);
  }
}
