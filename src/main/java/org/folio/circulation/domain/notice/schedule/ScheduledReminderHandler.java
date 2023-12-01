package org.folio.circulation.domain.notice.schedule;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.RemindersPolicy;
import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineOwnerRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.RecordNotFoundFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;
import org.folio.circulation.support.utils.DateFormatUtil;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.isNull;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createLoanNoticeContext;
import static org.folio.circulation.domain.representations.ContributorsToNamesMapper.mapContributorNamesToJson;
import static org.folio.circulation.support.results.Result.*;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.domain.notice.TemplateContextUtil.createFeeFineChargeNoticeContext;


/**
 * Extends {@link org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeHandler}
 * to additionally fetch the overdue fine policy when fetching loan, in order to get the
 * reminder fees policy;
 * to apply different logic for updating the notice after sending;
 * to additionally update the loan with the last reminder sent;
 * and to apply different isNoticeRelevant logic.
 * Reuses a handful of methods to set the notice contexts and fail if loan id is missing.
 */
public class ScheduledReminderHandler extends LoanScheduledNoticeHandler {

  private final ZonedDateTime systemTime;
  private final LoanPolicyRepository loanPolicyRepository;
  private final OverdueFinePolicyRepository overdueFinePolicyRepository;
  private final FeeFineOwnerRepository feeFineOwnerRepository;

  private final CalendarRepository calendarRepository;

  private final CollectionResourceClient accountsStorageClient;
  private final CollectionResourceClient feeFineActionsStorageClient;

  private final ConfigurationRepository configurationRepository;


  static final String ACCOUNT_FEE_FINE_ID_VALUE = "6b830703-f828-4e38-a0bb-ee81deacbd03";
  static final String ACCOUNT_FEE_FINE_TYPE_VALUE = "Reminder fee";
  static final String ACCOUNT_PAYMENT_STATUS_NAME_VALUE = "Outstanding";
  static final String ACCOUNT_STATUS_NAME_VALUE = "Open";



  public ScheduledReminderHandler(Clients clients, LoanRepository loanRepository) {
    super(clients, loanRepository);
    configurationRepository = new ConfigurationRepository(clients);
    this.systemTime = ClockUtil.getZonedDateTime();
    this.loanPolicyRepository = new LoanPolicyRepository(clients);
    this.calendarRepository = new CalendarRepository(clients);
    this.overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    this.feeFineOwnerRepository = new FeeFineOwnerRepository(clients);
    this.accountsStorageClient = clients.accountsStorageClient();
    this.feeFineActionsStorageClient = clients.feeFineActionsStorageClient();

    log.debug("Instantiated ScheduledReminderHandler");
  }

  @Override
  protected CompletableFuture<Result<ScheduledNotice>> handleContext(ScheduledNoticeContext context) {
    final ScheduledNotice notice = context.getNotice();
    return ofAsync(context)
      .thenCompose(r -> r.after(this::fetchNoticeData))
      .thenCompose(r -> r.after(when(this::isOpenDay, this::processNotice, this::skip)))
      .thenCompose(r -> handleResult(r, notice))
      .exceptionally(t -> handleException(t, notice));
  }

  private CompletableFuture<Result<ScheduledNotice>> processNotice(ScheduledNoticeContext context) {
    return ofAsync(context)
      .thenCompose(r -> r.after(this::persistAccount))
      .thenCompose(r -> r.after(this::createFeeFineAction))
      .thenCompose(r -> r.after(this::sendNotice))
      .thenCompose(r -> r.after(this::updateLoan))
      .thenCompose(r -> r.after(this::updateNotice));
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchLoan(
    ScheduledNoticeContext context) {
    // Also fetches user, item and item-related records (holdings, instance, location, etc.)
    return loanRepository.getById(context.getNotice().getLoanId())
      .thenCompose(r -> r.after(loanPolicyRepository::findPolicyForLoan))
      .thenCompose(overdueFinePolicyRepository::findOverdueFinePolicyForLoan)
      .thenApply(mapResult(context::withLoan))
      .thenApply(r -> {
        String servicePointId = r.value().getLoan().getCheckoutServicePointId();
        calendarRepository.lookupOpeningDays(ClockUtil.getZonedDateTime().toLocalDate(),servicePointId);
        return r;
      })
      .thenApply(r -> r.next(this::failWhenLoanIsIncomplete));
  }
//
  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(ScheduledNoticeContext context) {
    return ofAsync(() -> context)
      .thenApply(r -> r.next(this::failWhenNoticeHasNoLoanId))
      .thenCompose(r -> r.after(this::fetchTemplate))
      .thenCompose(r -> r.after(this::fetchLoan))
      .thenApply(r -> r.next(this::failWhenLoanHasNoNextReminderScheduled))
      .thenCompose(r -> r.after(this::instantiateReminderFeeAccount));
  }

  private CompletableFuture<Result<Boolean>> isOpenDay(ScheduledNoticeContext noticeContext) {
    String servicePointId = noticeContext.getLoan().getCheckoutServicePointId();
    return getSystemTimeInTenantsZone()
      .thenCompose(tenantTime -> {
        return calendarRepository.lookupOpeningDays(tenantTime.toLocalDate(),servicePointId)
          .thenCompose(days -> {
            Boolean openDay = days.value().getRequestedDay().isOpen();
            return ofAsync(openDay);
          });
      });
  }

  private CompletableFuture<ZonedDateTime> getSystemTimeInTenantsZone() {
    return configurationRepository
      .findTimeZoneConfiguration()
      .thenApply(tenantTimeZone -> systemTime.withZoneSameInstant(tenantTimeZone.value()));
  }

  private CompletableFuture<Result<ScheduledNotice>> skip(ScheduledNoticeContext previousResult) {
    return completedFuture(succeeded(previousResult.getNotice()));
  }

  protected Result<ScheduledNoticeContext> failWhenLoanHasNoNextReminderScheduled(ScheduledNoticeContext context) {
    RemindersPolicy.ReminderConfig nextReminder = context.getLoan().getNextReminder();

    return isNull(nextReminder)
      ? failed(new RecordNotFoundFailure("next scheduled reminder", "reminder-for-loan-"+context.getLoan().getId()))
      : succeeded(context);
  }
  /**
   * Sets current reminder as the most recent on the loan.
   */
  private CompletableFuture<Result<ScheduledNoticeContext>> updateLoan(ScheduledNoticeContext context) {
    if (isNoticeIrrelevant(context)) {
      return ofAsync(() -> context);
    }

    return loanRepository.updateLoan(
      context.getLoan().withIncrementedRemindersLastFeeBilled(systemTime))
      .thenApply(r -> r.map(v -> context));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> instantiateReminderFeeAccount(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    RemindersPolicy.ReminderConfig reminder = context.getLoan().getNextReminder();
    if (isNoticeIrrelevant(context) || reminder.hasZeroFee()) {
      return ofAsync(() -> context);
    }
    Item item = context.getLoan().getItem();
    ReminderFeeAccount reminderFeeAccount = new ReminderFeeAccount()
        .with(ReminderFeeAccount.FEE_FINE_ID, ACCOUNT_FEE_FINE_ID_VALUE)
        .with(ReminderFeeAccount.FEE_FINE_TYPE, ACCOUNT_FEE_FINE_TYPE_VALUE)
        .with(ReminderFeeAccount.AMOUNT, loan.getNextReminder().getReminderFee())
        .with(ReminderFeeAccount.REMAINING, loan.getNextReminder().getReminderFee())
        .with(ReminderFeeAccount.TITLE, item.getTitle())
        .with(ReminderFeeAccount.BARCODE, item.getBarcode())
        .with(ReminderFeeAccount.CALL_NUMBER, item.getCallNumber())
        .with(ReminderFeeAccount.LOCATION, item.getLocation().getName())
        .with(ReminderFeeAccount.MATERIAL_TYPE, item.getMaterialTypeName())
        .with(ReminderFeeAccount.MATERIAL_TYPE_ID, item.getMaterialTypeId())
        .with(ReminderFeeAccount.LOAN_ID, loan.getId())
        .with(ReminderFeeAccount.USER_ID, loan.getUserId())
        .with(ReminderFeeAccount.ITEM_ID, item.getItemId())
        .with(ReminderFeeAccount.DUE_DATE, loan.getDueDate())
        .withNamedObject(ReminderFeeAccount.PAYMENT_STATUS, ACCOUNT_PAYMENT_STATUS_NAME_VALUE)
        .withNamedObject(ReminderFeeAccount.STATUS, ACCOUNT_STATUS_NAME_VALUE)
        .with(ReminderFeeAccount.CONTRIBUTORS, mapContributorNamesToJson(item))
        .with(ReminderFeeAccount.LOAN_POLICY_ID, loan.getLoanPolicyId())
        .with(ReminderFeeAccount.OVERDUE_FINE_POLICY_ID, loan.getOverdueFinePolicyId())
        .with(ReminderFeeAccount.LOST_ITEM_FEE_POLICY_ID, loan.getLostItemPolicyId());

    return lookupFeeFineOwner(context)
        .thenApply(mapResult(reminderFeeAccount::withFeeFineOwner))
        .thenApply(r -> Result.succeeded(context.withAccount(Account.from(r.value().asJson()))));
  }

  private CompletableFuture<Result<FeeFineOwner>> lookupFeeFineOwner(ScheduledNoticeContext context) {
    return feeFineOwnerRepository
      .findOwnerForServicePoint(context.getLoan().getItem().getLocation().getPrimaryServicePoint().getId());
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> persistAccount(ScheduledNoticeContext context) {
    RemindersPolicy.ReminderConfig reminder = context.getLoan().getNextReminder();
    if (isNoticeIrrelevant(context) || reminder.hasZeroFee()) {
      return ofAsync(() -> context);
    }
    return accountsStorageClient.post(context.getAccount().toJson())
      .thenApply(r -> Result.succeeded(context));
  }

  private CompletableFuture<Result<ScheduledNoticeContext>> createFeeFineAction(ScheduledNoticeContext context) {
    RemindersPolicy.ReminderConfig reminder = context.getLoan().getNextReminder();
    if (isNoticeIrrelevant(context) || reminder.hasZeroFee()) {
      return ofAsync(() -> context);
    }
    Account account = context.getAccount();
    ReminderFeeAction reminderFeeAction =
      new ReminderFeeAction()
        .with(ReminderFeeAction.ACCOUNT_ID, account.getId())
        .with(ReminderFeeAction.USER_ID, account.getUserId())
        .with(ReminderFeeAction.BALANCE, account.getRemaining().toDouble())
        .with(ReminderFeeAction.AMOUNT_ACTION, account.getAmount().toDouble())
        .with(ReminderFeeAction.TYPE_ACTION, account.getFeeFineType())
        .with(ReminderFeeAction.SOURCE, ReminderFeeAction.CREATED_BY_SYSTEM)
        .with(ReminderFeeAction.CREATED_AT, context.getLoan().getItem().getLocation().getPrimaryServicePoint().getId())
        .with(ReminderFeeAction.COMMENTS, String.format("STAFF: Reminder fee %s", context.getLoan().getNextReminder().getSequenceNumber()))
        .with(ReminderFeeAction.DATE_ACTION, ClockUtil.getZonedDateTime());
    return feeFineActionsStorageClient.post(reminderFeeAction.asJson())
      .thenApply(r -> Result.succeeded(context.withChargeAction(new FeeFineAction(reminderFeeAction.asJson()))));
  }

  /**
   * Checks the loan for the most recent reminder sent, then updates the notice config of the scheduled notice
   * with the next step from the configured reminder sequence.
   * If there is no next reminder step, or if the loan was closed, the scheduled notice is deleted.
   */
  @Override
  protected CompletableFuture<Result<ScheduledNotice>> updateNotice(ScheduledNoticeContext context) {
    RemindersPolicy.ReminderConfig nextReminder = context.getLoan().getNextReminder();
    if (nextReminder == null) {
      return deleteNotice(context.getNotice(), "no more reminders scheduled");
    } else if (isNoticeIrrelevant(context)) {
      return deleteNotice(context.getNotice(), "further reminder notices became irrelevant");
    } else {
      return buildNextNotice(context, nextReminder)
        .thenCompose(scheduledNoticeResult -> scheduledNoticesRepository.update(scheduledNoticeResult.value()));
    }
  }

  protected CompletableFuture<Result<ScheduledNotice>> buildNextNotice (
    ScheduledNoticeContext context, RemindersPolicy.ReminderConfig nextReminder) {

    Loan loan = context.getLoan();
    Boolean canScheduleReminderUponClosedDate = loan.getOverdueFinePolicy().getRemindersPolicy().canScheduleReminderUponClosedDay();

    return configurationRepository.findTimeZoneConfiguration()
      .thenCompose(tenantTimeZone -> {
        return nextReminder
          .nextNoticeDueOn(
            systemTime,
            tenantTimeZone.value(),
            loan.getCheckoutServicePointId(),
            calendarRepository
          )
          .thenCompose(nextRunTimeResult -> {
            ScheduledNotice nextReminderNotice = context.getNotice()
              .withNextRunTime(nextRunTimeResult.value().truncatedTo(ChronoUnit.HOURS));
            nextReminderNotice.getConfiguration()
              .setTemplateId(nextReminder.getNoticeTemplateId())
              .setFormat(nextReminder.getNoticeFormat());
            return ofAsync(nextReminderNotice);
          });
      });
  }

  @Override
  protected boolean isNoticeIrrelevant(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    return loan.isClosed() || loan.hasItemWithAnyStatus(DECLARED_LOST, CLAIMED_RETURNED);
  }

  @Override
  protected JsonObject buildNoticeContextJson(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    if (loan.getNextReminder().hasZeroFee()) {
      return createLoanNoticeContext(loan);
    } else {
      return createFeeFineChargeNoticeContext(context.getAccount(), loan, context.getChargeAction());
    }
  }

  static class ReminderFeeAccount {
    static final String OWNER_ID = "ownerId";
    static final String FEE_FINE_ID = "feeFineId";
    static final String FEE_FINE_TYPE = "feeFineType";
    static final String AMOUNT = "amount";
    static final String REMAINING = "remaining";
    static final String FEE_FINE_OWNER = "feeFineOwner";
    static final String TITLE = "title";
    static final String BARCODE = "barcode";
    static final String CALL_NUMBER = "callNumber";
    static final String LOCATION = "location";
    static final String MATERIAL_TYPE = "materialType";
    static final String MATERIAL_TYPE_ID = "materialTypeId";
    static final String LOAN_ID = "loanId";
    static final String USER_ID = "userId";
    static final String ITEM_ID = "itemId";
    static final String DUE_DATE = "dueDate";
    static final String PAYMENT_STATUS = "paymentStatus";
    static final String STATUS = "status";
    static final String CONTRIBUTORS = "contributors";
    static final String LOAN_POLICY_ID = "loanPolicyId";
    static final String OVERDUE_FINE_POLICY_ID = "overdueFinePolicyId";
    static final String LOST_ITEM_FEE_POLICY_ID = "lostItemFeePolicyId";
    final JsonObject json = new JsonObject();

    ReminderFeeAccount() {
      json.put("id", UUID.randomUUID().toString());
    }

    ReminderFeeAccount with (String propertyName, String value) {
      json.put(propertyName, value);
      return this;
    }

    ReminderFeeAccount with (String propertyName, BigDecimal value) {
      json.put(propertyName, value);
      return this;
    }

    ReminderFeeAccount with (String propertyName, ZonedDateTime value) {
      json.put(propertyName, DateFormatUtil.formatDateTime(value.withZoneSameInstant(ZoneOffset.UTC)));
      return this;
    }

    ReminderFeeAccount with (String propertyName, JsonArray array) {
      json.put(propertyName, array);
      return this;
    }

    ReminderFeeAccount withNamedObject (String propertyName, String name) {
      json.put(propertyName, new JsonObject().put("name", name));
      return this;
    }

    ReminderFeeAccount withFeeFineOwner (FeeFineOwner owner) {
      if (owner != null) {
        json.put(FEE_FINE_OWNER, owner.getOwner());
        json.put(OWNER_ID, owner.getId());
      }
      return this;
    }

    JsonObject asJson() {
      return json;
    }

  }

  static class ReminderFeeAction {

    static final String CREATED_BY_SYSTEM = "System";
    final JsonObject json = new JsonObject();
    static final String USER_ID = "userId";
    static final String ACCOUNT_ID = "accountId";
    static final String SOURCE = "source";
    static final String CREATED_AT = "createdAt";
    static final String TRANSACTION_INFORMATION = "transactionInformation";
    static final String COMMENTS = "comments";
    static final String BALANCE = "balance";
    static final String AMOUNT_ACTION = "amountAction";
    static final String NOTIFY = "notify";
    static final String TYPE_ACTION = "typeAction";
    static final String DATE_ACTION = "dateAction";

    ReminderFeeAction() {
      json.put("id", UUID.randomUUID().toString());
      json.put(NOTIFY, false);
    }

    ReminderFeeAction with (String propertyName, String value) {
      json.put(propertyName, value);
      return this;
    }

    ReminderFeeAction with (String propertyName, Double value) {
      json.put(propertyName, value);
      return this;
    }

    ReminderFeeAction with (String propertyName, ZonedDateTime value) {
      json.put(propertyName, DateFormatUtil.formatDateTime(value.withZoneSameInstant(ZoneOffset.UTC)));
      return this;
    }

    JsonObject asJson() {
      return json;
    }

  }

}
