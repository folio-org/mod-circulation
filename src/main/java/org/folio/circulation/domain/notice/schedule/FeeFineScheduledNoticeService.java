package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.NoticeEventType.AGED_TO_LOST_FINE_CHARGED;
import static org.folio.circulation.domain.notice.NoticeEventType.AGED_TO_LOST_RETURNED;
import static org.folio.circulation.domain.notice.NoticeEventType.OVERDUE_FINE_RENEWED;
import static org.folio.circulation.domain.notice.NoticeEventType.OVERDUE_FINE_RETURNED;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.domain.subscribers.FeeFineBalanceChangedEvent;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.LostItemFeeRefundContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class FeeFineScheduledNoticeService {

  public static FeeFineScheduledNoticeService using(Clients clients) {
    return new FeeFineScheduledNoticeService(ScheduledNoticesRepository.using(clients),
      new PatronNoticePolicyRepository(clients), new FeeFineActionRepository(clients),
      new LoanRepository(clients, new ItemRepository(clients), new UserRepository(clients)));
  }

  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;
  private final FeeFineActionRepository feeFineActionRepository;
  private final LoanRepository loanRepository;

  public FeeFineScheduledNoticeService(ScheduledNoticesRepository scheduledNoticesRepository,
    PatronNoticePolicyRepository noticePolicyRepository,
    FeeFineActionRepository feeFineActionRepository, LoanRepository loanRepository) {

    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.noticePolicyRepository = noticePolicyRepository;
    this.feeFineActionRepository = feeFineActionRepository;
    this.loanRepository = loanRepository;
  }

  public Result<CheckInContext> scheduleOverdueFineNotices(CheckInContext context,
    FeeFineAction action) {

    scheduleNotices(context.getLoan(), action, OVERDUE_FINE_RETURNED);

    return succeeded(context);
  }

  public Result<RenewalContext> scheduleOverdueFineNotices(RenewalContext context) {
    scheduleNotices(context.getLoan(), context.getOverdueFeeFineAction(), OVERDUE_FINE_RENEWED);

    return succeeded(context);
  }

  public Result<LostItemFeeRefundContext> scheduleAgedToLostReturnedNotices(
    LostItemFeeRefundContext context, FeeFineAction feeFineAction) {

    scheduleNotices(context.getLoan(), feeFineAction, AGED_TO_LOST_RETURNED);

    return succeeded(context);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> scheduleNotices(
    Loan loan, FeeFineAction action, NoticeEventType eventType) {

    if (action == null) {
      return ofAsync(() -> null);
    }

    return noticePolicyRepository.lookupPolicy(loan)
      .thenCompose(r -> r.after(policy ->
        scheduleNoticeBasedOnPolicy(loan, policy, action, eventType)));
  }

  public CompletableFuture<Result<Void>> scheduleNoticesForAgedLostFeeFineCharged(
    Loan loan, Collection<FeeFineAction> actions) {

    actions.forEach(feeFineAction -> scheduleNotices(loan, feeFineAction,
      AGED_TO_LOST_FINE_CHARGED));

    return ofAsync(() -> null);
  }

  public CompletableFuture<Result<Void>> scheduleNoticesForLostItemFeeActualCost(
    FeeFineBalanceChangedEvent event) {

    return feeFineActionRepository.findByFeeFineId(event.getFeeFineId())
      .thenCompose(r -> r.after(feeFineActions -> scheduleNoticesForLostItemFeeActualCost(
        feeFineActions, event.getLoanId())));
  }

  private CompletableFuture<Result<Void>> scheduleNoticesForLostItemFeeActualCost(
    Collection<FeeFineAction> feeFineActions, String loanId) {

    return loanRepository.getById(loanId)
      .thenCompose(r -> r.after(loan -> scheduleNoticesForAgedLostFeeFineCharged(
        loan, feeFineActions)));
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> scheduleNoticeBasedOnPolicy(
    Loan loan, PatronNoticePolicy noticePolicy, FeeFineAction action, NoticeEventType eventType) {

    List<ScheduledNotice> scheduledNotices = noticePolicy.getNoticeConfigurations().stream()
      .filter(config -> config.getNoticeEventType() == eventType)
      .map(config -> createScheduledNotice(config, loan, action, eventType))
      .collect(Collectors.toList());

    return allOf(scheduledNotices, scheduledNoticesRepository::create);
  }

  private ScheduledNotice createScheduledNotice(NoticeConfiguration configuration,
    Loan loan, FeeFineAction action, NoticeEventType eventType) {

    return new ScheduledNoticeBuilder()
      .setId(UUID.randomUUID().toString())
      .setLoanId(loan.getId())
      .setFeeFineActionId(action.getId())
      .setRecipientUserId(loan.getUserId())
      .setNextRunTime(determineNextRunTime(configuration, action))
      .setNoticeConfig(createScheduledNoticeConfig(configuration))
      .setTriggeringEvent(TriggeringEvent.from(eventType))
      .build();
  }

  private ZonedDateTime determineNextRunTime(NoticeConfiguration configuration, FeeFineAction action) {
    ZonedDateTime actionDateTime = action.getDateAction();

    return configuration.getTiming() == NoticeTiming.AFTER
      ? configuration.getTimingPeriod().plusDate(actionDateTime)
      : actionDateTime;
  }

  private ScheduledNoticeConfig createScheduledNoticeConfig(NoticeConfiguration configuration) {
    return new ScheduledNoticeConfigBuilder()
      .setTemplateId(configuration.getTemplateId())
      .setTiming(configuration.getTiming())
      .setFormat(configuration.getNoticeFormat())
      .setRecurringPeriod(configuration.getRecurringPeriod())
      .setSendInRealTime(configuration.sendInRealTime())
      .build();
  }

}
