package org.folio.circulation.domain.notice.schedule;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.OverdueFinePolicyRemindersPolicy;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.results.Result.*;
import static org.folio.circulation.support.results.ResultBinding.mapResult;


/**
 * Extends {@link org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeHandler}
 * to additionally fetch the overdue fine policy when fetching loan, in order to get the
 * reminder fees policy;
 * to apply different logic for updating the notice after sending;
 * to additionally update the loan with the last reminder sent;
 * and to apply different isNoticeRelevant logic.
 * Reuses a handful of methods to set the notice contexts and fail if loan id is missing.
 */
public class LoanScheduledNoticeReminderFeeHandler extends LoanScheduledNoticeHandler {

  private final ZonedDateTime systemTime;
  private final LoanPolicyRepository loanPolicyRepository;
  private final OverdueFinePolicyRepository overdueFinePolicyRepository;

  public LoanScheduledNoticeReminderFeeHandler(Clients clients, LoanRepository loanRepository) {
    super(clients, loanRepository);
    this.systemTime = ClockUtil.getZonedDateTime();
    this.loanPolicyRepository = new LoanPolicyRepository(clients);
    this.overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    log.debug("Instantiated LoanScheduledNoticeReminderFeeHandler");
  }

  protected CompletableFuture<Result<ScheduledNotice>> handleContext(ScheduledNoticeContext context) {
    final ScheduledNotice notice = context.getNotice();

    return ofAsync(context)
      .thenCompose(r -> r.after(this::fetchNoticeData))
      .thenCompose(r -> r.after(this::sendNotice))
      .thenCompose(r -> r.after(this::updateLoan))
      .thenCompose(r -> r.after(this::updateNotice))
      .thenCompose(r -> handleResult(r, notice))
      .exceptionally(t -> handleException(t, notice));
  }

  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchLoan(
    ScheduledNoticeContext context) {

    // Also fetches user, item and item-related records (holdings, instance, location, etc.)
    return loanRepository.getById(context.getNotice().getLoanId())
      .thenCompose(r -> r.after(loanPolicyRepository::findPolicyForLoan))
      .thenCompose(overdueFinePolicyRepository::findOverdueFinePolicyForLoan)
      .thenApply(mapResult(context::withLoan))
      .thenApply(r -> r.next(this::failWhenLoanIsIncomplete));
  }


  @Override
  protected CompletableFuture<Result<ScheduledNoticeContext>> fetchData(ScheduledNoticeContext context) {
    return ofAsync(() -> context)
      .thenApply(r -> r.next(this::failWhenNoticeHasNoLoanId))
      .thenCompose(r -> r.after(this::fetchTemplate))
      .thenCompose(r -> r.after(this::fetchLoan));
  }

  /**
   * Sets current reminder as the most recent on the loan.
   */
  private CompletableFuture<Result<ScheduledNoticeContext>> updateLoan(ScheduledNoticeContext context) {
    return loanRepository.updateLoan(
      context.getLoan().withIncrementedRemindersLastFeeBilled(systemTime))
      .thenApply(r -> r.map(v -> context));
  }

  /**
   * Checks the loan for the most recent reminder sent, then updates the notice config of the scheduled notice
   * with the next step from the configured reminder sequence.
   * If there is no next reminder step, or if the loan was closed, the scheduled notice is deleted.
   */
  @Override
  protected CompletableFuture<Result<ScheduledNotice>> updateNotice(ScheduledNoticeContext context) {
    Loan loan = context.getLoan();
    Integer latestReminderNumber = loan.getLastReminderFeeBilledNumber();
    OverdueFinePolicyRemindersPolicy.ReminderSequence schedule = loan.getOverdueFinePolicy().getRemindersPolicy().getReminderSchedule();
    OverdueFinePolicyRemindersPolicy.ReminderSequenceEntry nextEntry = schedule.getEntryAfter(latestReminderNumber);
    if (nextEntry == null) {
      return deleteNotice(context.getNotice(), "no more reminders scheduled");
    } else if (isNoticeIrrelevant(context)) {
      return deleteNotice(context.getNotice(), "further reminder notices became irrelevant");
    } else {
      ScheduledNotice nextReminderNotice = context.getNotice()
        .withNextRunTime(nextEntry.getPeriod().plusDate(systemTime));
      nextReminderNotice.getConfiguration()
        .setTemplateId(nextEntry.getNoticeTemplateId())
        .setFormat(nextEntry.getNoticeFormat());

      return scheduledNoticesRepository.update(nextReminderNotice);
    }
  }

  @Override
  protected boolean isNoticeIrrelevant(ScheduledNoticeContext context) {
    return dueDateNoticeIsNotRelevant(context);
  }

}
