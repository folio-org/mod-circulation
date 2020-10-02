package org.folio.circulation.domain.notice.schedule;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticeService;
import org.folio.circulation.domain.notice.TemplateContextUtil;
import org.folio.circulation.domain.representations.logs.NoticeLogContextItem;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DueDateScheduledNoticeHandler {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String USER_RECORD_TYPE = "user";
  private static final String ITEM_RECORD_TYPE = "item";
  private static final String LOAN_RECORD_TYPE = "loan";
  private static final String TEMPLATE_RECORD_TYPE = "template";
  static final String[] REQUIRED_RECORD_TYPES = {USER_RECORD_TYPE,
    ITEM_RECORD_TYPE, LOAN_RECORD_TYPE, TEMPLATE_RECORD_TYPE};

  public static DueDateScheduledNoticeHandler using(Clients clients, DateTime systemTime) {
    return new DueDateScheduledNoticeHandler(
      new LoanRepository(clients),
      new LoanPolicyRepository(clients),
      new ConfigurationRepository(clients),
      new PatronNoticePolicyRepository(clients),
      PatronNoticeService.using(clients),
      ScheduledNoticesRepository.using(clients),
      clients.templateNoticeClient(), systemTime);
  }

  private final LoanRepository loanRepository;
  private final LoanPolicyRepository loanPolicyRepository;
  private final ConfigurationRepository configurationRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;
  private final PatronNoticeService patronNoticeService;
  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final CollectionResourceClient templateNoticesClient;
  private final DateTime systemTime;

  public CompletableFuture<Result<Collection<ScheduledNotice>>> handleNotices(
    Collection<ScheduledNotice> scheduledNotices) {

    CompletableFuture<Result<ScheduledNotice>> future = completedFuture(succeeded(null));
    for (ScheduledNotice scheduledNotice : scheduledNotices) {
      future = future.thenCompose(r -> handleNotice(scheduledNotice));
    }
    return future.thenApply(r -> r.map(v -> scheduledNotices));
  }

  private CompletableFuture<Result<ScheduledNotice>> handleNotice(ScheduledNotice notice) {
    if (notice.getLoanId() != null) {
      return handleDueDateNotice(notice);
    }
    return completedFuture(succeeded(notice));
  }

  private CompletableFuture<Result<ScheduledNotice>> handleDueDateNotice(ScheduledNotice notice) {
    String templateId = notice.getConfiguration().getTemplateId();

    return templateNoticesClient.get(templateId)
      .thenApply(r -> r.next(response -> failIfTemplateNotFound(response, templateId)))
      .thenCompose(r -> r.after(i -> loanRepository.getById(notice.getLoanId())))
      .thenCompose(r -> deleteNoticeIfLoanIsMissingOrIncomplete(r, notice))
      .thenApply(r -> r.map(LoanAndRelatedRecords::new))
      .thenCompose(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration, LoanAndRelatedRecords::withTimeZone))
      .thenCompose(r -> r.after(records -> sendNotice(records, notice)))
      .thenCompose(r -> r.after(relatedRecords -> updateNotice(relatedRecords, notice)))
      .thenApply(r -> r.mapFailure(this::handleFailure));
  }

  public Result<Response> failIfTemplateNotFound(Response response, String templateId) {
    if (response.getStatusCode() == 404) {
      return failed(new RecordNotFoundFailure(TEMPLATE_RECORD_TYPE, templateId));
    } else {
      return succeeded(response);
    }
  }

  CompletableFuture<Result<Loan>> deleteNoticeIfLoanIsMissingOrIncomplete(
      Result<Loan> result, ScheduledNotice notice) {

    if (failedToFindRecordOfType(result, LOAN_RECORD_TYPE)) {
      return deleteInvalidNoticeAndFail(notice, LOAN_RECORD_TYPE, notice.getLoanId());
    }

    if (failedToFindRecordOfType(result, TEMPLATE_RECORD_TYPE)) {
      return deleteInvalidNoticeAndFail(notice, TEMPLATE_RECORD_TYPE,
        notice.getConfiguration().getTemplateId());
    }

    if (result.succeeded()) {
      Loan loan = result.value();
      if (loan.getItem().isNotFound()) {
        return deleteInvalidNoticeAndFail(notice, ITEM_RECORD_TYPE, loan.getItemId());
      }
      if (loan.getUser() == null) {
        return deleteInvalidNoticeAndFail(notice, USER_RECORD_TYPE, loan.getUserId());
      }
    }
    return completedFuture(result);
  }

  private CompletableFuture<Result<Loan>> deleteInvalidNoticeAndFail(
      ScheduledNotice notice, String recordType, String recordId) {

    log.info("Deleting scheduled notice {} as referenced {} {} was not found", notice.getId(), recordType, recordId);
    return scheduledNoticesRepository.delete(notice)
      .thenApply(r -> r.next(n -> failed(new RecordNotFoundFailure(recordType, recordId))));
  }

  private Result<ScheduledNotice> handleFailure(HttpFailure failure) {
    if (isRecordNotFoundFailureForType(failure, REQUIRED_RECORD_TYPES)) {
      return succeeded(null);
    }
    return failed(failure);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> sendNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNotice notice) {
    Loan loan = relatedRecords.getLoan();

    if (noticeIsNotRelevant(notice, loan)) {
      return completedFuture(succeeded(relatedRecords));
    }

    JsonObject loanNoticeContext = TemplateContextUtil.createLoanNoticeContext(loan);

    NoticeLogContextItem logContextItem = NoticeLogContextItem.from(loan)
      .withTemplateId(notice.getConfiguration().getTemplateId())
      .withTriggeringEvent(notice.getTriggeringEvent().getRepresentation());

    return noticePolicyRepository.lookupPolicyId(loan.getItem(), loan.getUser())
      .thenCompose(r -> r.after(policy -> patronNoticeService.acceptScheduledNoticeEvent(
        notice.getConfiguration(), relatedRecords.getUserId(), loanNoticeContext,
        new NoticeLogContext().withUser(loan.getUser())
          .withItems(Collections.singletonList(logContextItem.withNoticePolicyId(policy.getPolicyId()))))))
      .thenApply(r -> r.map(v -> relatedRecords));
  }

  public CompletableFuture<Result<ScheduledNotice>> updateNotice(
    LoanAndRelatedRecords relatedRecords, ScheduledNotice notice) {

    Loan loan = relatedRecords.getLoan();
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    if (loan.isClosed() || !noticeConfig.isRecurring()) {
      return scheduledNoticesRepository.delete(notice);
    }

    DateTime recurringNoticeNextRunTime = notice.getNextRunTime()
      .plus(noticeConfig.getRecurringPeriod().timePeriod());

    if (recurringNoticeNextRunTime.isBefore(systemTime)) {
      recurringNoticeNextRunTime =
        systemTime.plus(noticeConfig.getRecurringPeriod().timePeriod());
    }

    ScheduledNotice nextRecurringNotice = notice.withNextRunTime(recurringNoticeNextRunTime);

    if (nextRecurringNoticeIsNotRelevant(nextRecurringNotice, loan)) {
      return scheduledNoticesRepository.delete(notice);
    }

    return scheduledNoticesRepository.update(nextRecurringNotice);
  }

  public boolean noticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    return loan.isClosed() || beforeNoticeIsNotRelevant(notice, loan);
  }

  private boolean beforeNoticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return noticeConfig.getTiming() == NoticeTiming.BEFORE &&
      loan.getDueDate().isBefore(systemTime);
  }

  private boolean nextRecurringNoticeIsNotRelevant(ScheduledNotice notice, Loan loan) {
    ScheduledNoticeConfig noticeConfig = notice.getConfiguration();

    return noticeConfig.isRecurring() &&
      noticeConfig.getTiming() == NoticeTiming.BEFORE &&
      notice.getNextRunTime().isAfter(loan.getDueDate());
  }

  <T> boolean failedToFindRecordOfType(Result<T> result, String... recordTypes) {
    return result.failed()
      && isRecordNotFoundFailureForType(result.cause(), recordTypes);
  }

  private boolean isRecordNotFoundFailureForType(HttpFailure failure, String... recordTypes) {
    return failure instanceof RecordNotFoundFailure
        && StringUtils.equalsAny(((RecordNotFoundFailure) failure).getRecordType(), recordTypes);
  }
}
