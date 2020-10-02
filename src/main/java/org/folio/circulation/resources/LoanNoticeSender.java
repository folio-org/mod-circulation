package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class LoanNoticeSender {

  public static LoanNoticeSender using(Clients clients) {
    return new LoanNoticeSender(
      PatronNoticeService.using(clients),
      new LoanPolicyRepository(clients));
  }

  private final PatronNoticeService patronNoticeService;
  private final LoanPolicyRepository loanPolicyRepository;

  public LoanNoticeSender(PatronNoticeService patronNoticeService, LoanPolicyRepository loanPolicyRepository) {
    this.patronNoticeService = patronNoticeService;
    this.loanPolicyRepository = loanPolicyRepository;
  }

  public Result<RenewalContext> sendRenewalPatronNotice(RenewalContext records) {
    sendLoanNotice(records.getLoan(), NoticeEventType.RENEWED);
    return succeeded(records);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> sendManualDueDateChangeNotice(LoanAndRelatedRecords records) {
    if (records.getLoan().getUser() == null) {
      return completedFuture(succeeded(records));
    }

    return loanPolicyRepository.lookupLoanPolicy(records)
      .thenApply(r -> r.next(recordsWithPolicy -> {
        sendLoanNotice(recordsWithPolicy, NoticeEventType.MANUAL_DUE_DATE_CHANGE);
        return succeeded(records);
      }));
  }

  private void sendLoanNotice(LoanAndRelatedRecords records, NoticeEventType eventType) {
    sendLoanNotice(records.getLoan(), eventType);
  }

  private void sendLoanNotice(Loan loan, NoticeEventType eventType) {
    JsonObject noticeContext = createLoanNoticeContext(loan);

    PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
      .withItem(loan.getItem())
      .withUser(loan.getUser())
      .withEventType(eventType)
      .withNoticeContext(noticeContext)
      .build();

    patronNoticeService.acceptNoticeEvent(noticeEvent, NoticeLogContext.from(loan));
  }
}
