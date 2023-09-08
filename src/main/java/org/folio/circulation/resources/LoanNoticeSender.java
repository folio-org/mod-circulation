package org.folio.circulation.resources;

import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.notice.ImmediatePatronNoticeService;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.SingleImmediatePatronNoticeService;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class LoanNoticeSender {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final ImmediatePatronNoticeService patronNoticeService;
  private final LoanPolicyRepository loanPolicyRepository;
  private final EventPublisher eventPublisher;
  private final LoanRepository loanRepository;

  public static LoanNoticeSender using(Clients clients, LoanRepository loanRepository) {
    return new LoanNoticeSender(
      new SingleImmediatePatronNoticeService(clients),
      new LoanPolicyRepository(clients),
      new EventPublisher(clients.pubSubPublishingService()),
      loanRepository
    );
  }

  public Result<RenewalContext> sendRenewalPatronNotice(RenewalContext records) {
    sendLoanNotice(records.getLoan(), NoticeEventType.RENEWED);
    return succeeded(records);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> sendManualDueDateChangeNotice(
    LoanAndRelatedRecords records) {

    return loanPolicyRepository.lookupLoanPolicy(records)
      .thenApply(r -> r.next(recordsWithPolicy -> {
        sendLoanNotice(recordsWithPolicy, NoticeEventType.MANUAL_DUE_DATE_CHANGE);
        return succeeded(records);
      }));
  }

  private void sendLoanNotice(LoanAndRelatedRecords records, NoticeEventType eventType) {
    sendLoanNotice(records.getLoan(), eventType);
  }

  private CompletableFuture<Result<Void>> sendLoanNotice(Loan loan, NoticeEventType eventType) {
    return succeeded(loan)
      .next(this::validateLoan)
      .next(l -> loanRepository.fetchLatestPatronInfoAddedComment(l).join())
      .mapFailure(failure -> publishNoticeErrorEvent(failure, loan, eventType))
      .after(l -> sendNotice(loan, eventType));
  }

  private Result<Loan> validateLoan(Loan loan) {
    List<ValidationError> errors = new ArrayList<>();

    if (loan == null) {
      errors.add(new ValidationError("Loan is null", "loan", null));
    } else {
      if (loan.getUser() == null) {
        errors.add(new ValidationError("User is null", "user", null));
      }
      if (loan.getItem() == null || loan.getItem().isNotFound()) {
        errors.add(new ValidationError("Item is null", "item", null));
      }
    }

    return errors.isEmpty()
      ? succeeded(loan)
      : failedValidation(errors);
  }

  private Result<Loan> publishNoticeErrorEvent(HttpFailure failure, Loan loan,
    NoticeEventType eventType) {

    if (loan == null) {
      log.error("Failed to send {} notice and circulation log event: loan is null", eventType);
    } else {
      NoticeLogContext noticeLogContext = NoticeLogContext.from(loan)
        .withTriggeringEvent(eventType.getRepresentation());
      eventPublisher.publishNoticeErrorLogEvent(noticeLogContext, failure);
    }

    return failed(failure);
  }


  private CompletableFuture<Result<Void>> sendNotice(Loan loan, NoticeEventType eventType) {
    PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
      .withItem(loan.getItem())
      .withUser(loan.getUser())
      .withEventType(eventType)
      .withNoticeContext(createLoanNoticeContext(loan))
      .withNoticeLogContext(NoticeLogContext.from(loan))
      .build();

    return patronNoticeService.acceptNoticeEvent(noticeEvent);
  }

}
