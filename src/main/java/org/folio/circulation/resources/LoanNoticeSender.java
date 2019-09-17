package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.support.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.notice.PatronNoticeEventBuilder;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

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

  public Result<LoanAndRelatedRecords> sendCheckOutPatronNotice(LoanAndRelatedRecords records) {
    sendLoanNotice(records, NoticeEventType.CHECK_OUT);
    return succeeded(records);
  }

  public Result<LoanAndRelatedRecords> sendRenewalPatronNotice(LoanAndRelatedRecords records) {
    sendLoanNotice(records, NoticeEventType.RENEWED);
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
    final Loan loan = records.getLoan();

    JsonObject noticeContext = createLoanNoticeContext(loan);

    PatronNoticeEvent noticeEvent = new PatronNoticeEventBuilder()
      .withItem(loan.getItem())
      .withUser(loan.getUser())
      .withEventType(eventType)
      .withTiming(NoticeTiming.UPON_AT)
      .withNoticeContext(noticeContext)
      .build();

    patronNoticeService.acceptNoticeEvent(noticeEvent);
  }
}
