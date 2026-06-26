package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.domain.notice.NoticeEventType.AGED_TO_LOST_FINE_CHARGED;
import static org.folio.circulation.domain.notice.NoticeEventType.AGED_TO_LOST_RETURNED;
import static org.folio.circulation.domain.notice.NoticeEventType.OVERDUE_FINE_RENEWED;
import static org.folio.circulation.domain.notice.NoticeEventType.OVERDUE_FINE_RETURNED;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.domain.subscribers.FeeFineBalanceChangedEvent;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
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
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static FeeFineScheduledNoticeService using(Clients clients) {
    return new FeeFineScheduledNoticeService(ScheduledNoticesRepository.using(clients),
      new PatronNoticePolicyRepository(clients), new FeeFineActionRepository(clients),
      new LoanRepository(clients, new ItemRepository(clients), new UserRepository(clients)),
      new AccountRepository(clients));
  }

  private final ScheduledNoticesRepository scheduledNoticesRepository;
  private final PatronNoticePolicyRepository noticePolicyRepository;
  private final FeeFineActionRepository feeFineActionRepository;
  private final LoanRepository loanRepository;
  private final AccountRepository accountRepository;

  public FeeFineScheduledNoticeService(ScheduledNoticesRepository scheduledNoticesRepository,
    PatronNoticePolicyRepository noticePolicyRepository,
    FeeFineActionRepository feeFineActionRepository, LoanRepository loanRepository,
    AccountRepository accountRepository) {

    this.scheduledNoticesRepository = scheduledNoticesRepository;
    this.noticePolicyRepository = noticePolicyRepository;
    this.feeFineActionRepository = feeFineActionRepository;
    this.loanRepository = loanRepository;
    this.accountRepository = accountRepository;
  }

  public Result<CheckInContext> scheduleOverdueFineNotices(CheckInContext context,
    FeeFineAction action) {

    log.info("scheduleOverdueFineNotices:: scheduling overdue fine notices for check-in, loan {}, action {}",
      context.getLoan() != null ? context.getLoan().getId() : "null", action != null ? action.getId() : "null");

    scheduleNotices(context.getLoan(), action, OVERDUE_FINE_RETURNED, context.getSessionId());

    return succeeded(context);
  }

  public Result<RenewalContext> scheduleOverdueFineNotices(RenewalContext context) {
    log.info("scheduleOverdueFineNotices:: scheduling overdue fine notices for renewal, loan {}",
      context.getLoan() != null ? context.getLoan().getId() : "null");
    scheduleNotices(context.getLoan(), context.getOverdueFeeFineAction(), OVERDUE_FINE_RENEWED);

    return succeeded(context);
  }

  public Result<LostItemFeeRefundContext> scheduleAgedToLostReturnedNotices(
    LostItemFeeRefundContext context, FeeFineAction feeFineAction) {

    log.info("scheduleAgedToLostReturnedNotices:: scheduling aged to lost returned notices, loan {}, action {}",
      context.getLoan() != null ? context.getLoan().getId() : "null",
      feeFineAction != null ? feeFineAction.getId() : "null");

    scheduleNotices(context.getLoan(), feeFineAction, AGED_TO_LOST_RETURNED);

    return succeeded(context);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> scheduleNotices(
    Loan loan, FeeFineAction action, NoticeEventType eventType) {

    log.info("scheduleNotices:: scheduling notices for event type {}, loan {}", eventType,
      loan != null ? loan.getId() : "null");
    return scheduleNotices(loan, action, eventType, null);
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> scheduleNotices(
    Loan loan, FeeFineAction action, NoticeEventType eventType, UUID sessionId) {
    log.info("scheduleNotices:: scheduling notices with session, event type {}, loan {}, sessionId {}",
      eventType, loan != null ? loan.getId() : "null", sessionId);
    if (action == null) {
      log.info("scheduleNotices:: action is null, no notices to schedule");
      return ofAsync(() -> null);
    }

    return noticePolicyRepository.lookupPolicy(loan)
      .thenCompose(r -> r.after(policy ->
        scheduleNoticeBasedOnPolicy(loan, policy, action, eventType, sessionId)));
  }

  public CompletableFuture<Result<Void>> scheduleNoticesForLostItemFeeActualCost(
    FeeFineBalanceChangedEvent event) {
    log.info("scheduleNoticesForLostItemFeeActualCost:: scheduling notices for actual cost, " +
        "feeFineId {}, loanId {}", event::getFeeFineId, event::getLoanId);
    accountRepository.findById(event.getFeeFineId())
      .thenCompose(r -> r.after(feeFineActionRepository::findChargeActionForAccount))
      .thenCombine(loanRepository.getById(event.getLoanId()), (a, l) -> a.combine(l,
        (action, loan) -> scheduleNotices(loan, action, AGED_TO_LOST_FINE_CHARGED)));
    return emptyAsync();
  }

  public CompletableFuture<Result<Void>> scheduleNoticesForAgedLostFeeFineCharged(
    Loan loan, Collection<FeeFineAction> actions) {

    log.info("scheduleNoticesForAgedLostFeeFineCharged:: scheduling notices for {} " +
        "aged lost fee/fine actions, loan {}", actions.size(), loan != null ? loan.getId() : "null");
    actions.forEach(feeFineAction -> scheduleNotices(loan, feeFineAction,
      AGED_TO_LOST_FINE_CHARGED));

    return emptyAsync();
  }

  private CompletableFuture<Result<List<ScheduledNotice>>> scheduleNoticeBasedOnPolicy(
    Loan loan, PatronNoticePolicy noticePolicy, FeeFineAction action, NoticeEventType eventType,
    UUID sessionId) {

    log.info("scheduleNoticeBasedOnPolicy:: scheduling notice based on policy, event type {}, loan {}",
      eventType, loan != null ? loan.getId() : "null");
    List<ScheduledNotice> scheduledNotices = noticePolicy.getNoticeConfigurations().stream()
      .filter(config -> config.getNoticeEventType() == eventType)
      .map(config -> createScheduledNotice(config, loan, action, eventType, sessionId))
      .collect(Collectors.toList());
    log.info("scheduleNoticeBasedOnPolicy:: created {} scheduled notices for event type {}",
      scheduledNotices.size(), eventType);

    return allOf(scheduledNotices, scheduledNoticesRepository::create);
  }

  private ScheduledNotice createScheduledNotice(NoticeConfiguration configuration,
    Loan loan, FeeFineAction action, NoticeEventType eventType, UUID sessionId) {

    log.info("createScheduledNotice:: creating scheduled notice for event type {}, loan {}, action {}",
      () -> eventType, loan::getId, action::getId);

    return new ScheduledNoticeBuilder()
      .setId(UUID.randomUUID().toString())
      .setLoanId(loan.getId())
      .setFeeFineActionId(action.getId())
      .setRecipientUserId(loan.getUserId())
      .setNextRunTime(determineNextRunTime(configuration, action))
      .setNoticeConfig(createScheduledNoticeConfig(configuration))
      .setTriggeringEvent(TriggeringEvent.from(eventType))
      .setSessionId(sessionId == null ? null : sessionId.toString())
      .build();
  }

  private ZonedDateTime determineNextRunTime(NoticeConfiguration configuration, FeeFineAction action) {
    log.info("determineNextRunTime:: determining next run time for timing {}",
      configuration::getTiming);
    ZonedDateTime actionDateTime = action.getDateAction();

    return configuration.getTiming() == NoticeTiming.AFTER
      ? configuration.getTimingPeriod().plusDate(actionDateTime)
      : actionDateTime;
  }

  private ScheduledNoticeConfig createScheduledNoticeConfig(NoticeConfiguration configuration) {
    log.info("createScheduledNoticeConfig:: creating scheduled notice config with template {}",
      configuration::getTemplateId);

    return new ScheduledNoticeConfigBuilder()
      .setTemplateId(configuration.getTemplateId())
      .setTiming(configuration.getTiming())
      .setFormat(configuration.getNoticeFormat())
      .setRecurringPeriod(configuration.getRecurringPeriod())
      .setSendInRealTime(configuration.sendInRealTime())
      .build();
  }
}
