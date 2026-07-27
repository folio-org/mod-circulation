package org.folio.circulation.domain.anonymization.service;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.Clock;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.AnonymizationEligibility;
import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizationDueDateStorageRepository;
import org.folio.circulation.support.results.Result;

/**
 * Evaluation pass of the scheduled anonymization job: visits closed loans with
 * no due-date row yet, computes each loan's due instant with
 * {@link AnonymizationEligibility}, and upserts the page (a real instant, or
 * {@code null} for retain). Every stamped loan leaves the unevaluated set, so
 * the sweep advances to empty; the {@code LIMIT} bounds per-run work.
 *
 * <p>The configuration is read once per run: a loan_history change mid-run can
 * leave one page computed under the previous policy. Over-early stamps are
 * caught by the drain's re-check; over-late ones are cleared by the next
 * loan_history save.</p>
 */
public class AnonymizationDueDateEvaluationService extends DefaultLoansFinder {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final AnonymizationDueDateStorageRepository dueDateStorageRepository;
  private final LoanAnonymizationConfiguration config;
  private final Clock clock;
  private final int pageLimit;

  public AnonymizationDueDateEvaluationService(AccountRepository accountRepository,
    AnonymizationDueDateStorageRepository dueDateStorageRepository,
    LoanAnonymizationConfiguration config, Clock clock, int pageLimit) {

    super(accountRepository);
    this.dueDateStorageRepository = dueDateStorageRepository;
    this.config = config;
    this.clock = clock;
    this.pageLimit = pageLimit;
  }

  /** Outcome of one evaluation pass, for the run's metrics log. */
  public record EvaluationStats(int evaluated, int stampedNever, int stamped) { }

  /** Evaluates and stamps one page of not-yet-evaluated closed loans. */
  public CompletableFuture<Result<EvaluationStats>> evaluateOnePage() {
    log.info("evaluateOnePage:: sweeping up to {} unevaluated closed loans", pageLimit);

    return dueDateStorageRepository.findUnevaluated(pageLimit)
      .thenCompose(this::fetchAdditionalLoanInfo)
      .thenCompose(r -> r.after(this::stampPage));
  }

  private CompletableFuture<Result<EvaluationStats>> stampPage(Collection<Loan> loans) {
    final Map<String, ZonedDateTime> dueDatesByLoanId = buildStampMap(loans);
    final int evaluated = dueDatesByLoanId.size();

    if (evaluated == 0) {
      log.info("stampPage:: nothing to evaluate (sweep is drained)");
      return completedFuture(succeeded(new EvaluationStats(0, 0, 0)));
    }

    final int neverCount = (int) dueDatesByLoanId.values().stream()
      .filter(Objects::isNull).count();

    return dueDateStorageRepository.stamp(dueDatesByLoanId)
      .thenApply(r -> r.map(updated -> {
        log.info("stampPage:: evaluated {}, stamped {} ({} never)",
          evaluated, updated, neverCount);
        return new EvaluationStats(evaluated, neverCount, updated);
      }));
  }

  /**
   * Re-stamps loans that were due but not eligible on re-check (e.g. a fee
   * charged on an already-stamped loan), so they leave the due set instead of
   * being re-fetched every run. Returns the records unchanged.
   */
  public CompletableFuture<Result<LoanAnonymizationRecords>> restampBlocked(
    LoanAnonymizationRecords records) {

    final Set<String> blockedIds = records.getNotAnonymizedLoans().values().stream()
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());

    final Collection<Loan> blockedLoans = records.getLoansFound().stream()
      .filter(loan -> blockedIds.contains(loan.getId()))
      .toList();

    final Map<String, ZonedDateTime> dueDatesByLoanId = buildStampMap(blockedLoans);

    if (dueDatesByLoanId.isEmpty()) {
      return completedFuture(succeeded(records));
    }

    log.info("restampBlocked:: re-stamping {} no-longer-eligible loans",
      dueDatesByLoanId.size());

    return dueDateStorageRepository.stamp(dueDatesByLoanId)
      .thenApply(r -> r.map(updated -> records));
  }

  /**
   * Loan id → due instant (or {@code null} for retain) for every closed loan in
   * the page. LinkedHashMap so null values are kept as entries.
   */
  private Map<String, ZonedDateTime> buildStampMap(Collection<Loan> loans) {
    final Map<String, ZonedDateTime> dueDatesByLoanId = new LinkedHashMap<>();
    for (Loan loan : loans) {
      if (loan.isClosed()) {
        dueDatesByLoanId.put(loan.getId(),
          AnonymizationEligibility.dueAt(loan, config, clock).orElse(null));
      }
    }
    return dueDatesByLoanId;
  }
}
