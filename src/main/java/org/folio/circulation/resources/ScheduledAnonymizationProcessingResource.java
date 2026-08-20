package org.folio.circulation.resources;

import static org.folio.circulation.support.results.AsynchronousResultBindings.safelyInitialise;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.Environment;
import org.folio.circulation.Clock;
import org.folio.circulation.domain.anonymization.DefaultLoanAnonymizationService;
import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.domain.anonymization.service.AnonymizationDueDateEvaluationService;
import org.folio.circulation.domain.anonymization.service.AnonymizationDueDateEvaluationService.EvaluationStats;
import org.folio.circulation.domain.anonymization.service.AnonymizationEligibilityService;
import org.folio.circulation.domain.anonymization.service.LoansForTenantFinder;
import org.folio.circulation.domain.representations.anonymization.AnonymizeLoansRepresentation;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizationDueDateStorageRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.services.CirculationSettingsService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Automatic loan anonymization based on the loan_history setting, run at short
 * intervals. Two passes per run:
 * <ol>
 *   <li><b>Evaluate:</b> stamp a due-date on one page of closed loans that have
 *   no row yet. Skipped when the tenant policy is "never".</li>
 *   <li><b>Drain:</b> fetch loans whose due-date has arrived, re-verify against
 *   the live rules, and strip the verified ones (the strip deletes the row).
 *   Loans no longer eligible are re-stamped so they leave the due set.</li>
 * </ol>
 * Evaluate runs before drain so a loan already past-due is stripped in the same
 * run.
 */
public class ScheduledAnonymizationProcessingResource extends Resource {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public ScheduledAnonymizationProcessingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/scheduled-anonymize-processing", router)
    .create(this::scheduledAnonymizeLoans);
  }

  private void scheduledAnonymizeLoans(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final var circulationSettingsService = new CirculationSettingsService(clients);
    final var accountRepository = new AccountRepository(clients);
    final var anonymizeStorageLoansRepository = new AnonymizeStorageLoansRepository(clients);
    final var dueDateStorageRepository = new AnonymizationDueDateStorageRepository(clients);
    final var eventPublisher = new EventPublisher(clients);
    final Clock clock = ClockUtil::getZonedDateTime;

    final var loansFinder = new LoansForTenantFinder(dueDateStorageRepository, accountRepository,
      Environment.getScheduledAnonymizationNumberOfLoansToCheck());

    log.info("Initializing loan anonymization for current tenant");

    safelyInitialise(circulationSettingsService::getLoanAnonymizationSettings)
      .thenCompose(r -> r.after(config -> {
        final var eligibilityService = new AnonymizationEligibilityService(config, clock);
        final var anonymizationService = new DefaultLoanAnonymizationService(
          eligibilityService, anonymizeStorageLoansRepository, eventPublisher);
        final var evaluationService = new AnonymizationDueDateEvaluationService(
          accountRepository, dueDateStorageRepository, config, clock,
          Environment.getScheduledAnonymizationEvaluationPageSize());

        return runBothPasses(eligibilityService, anonymizationService, evaluationService,
          loansFinder);
      }))
      .thenApply(AnonymizeLoansRepresentation::from)
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<LoanAnonymizationRecords>> runBothPasses(
    AnonymizationEligibilityService eligibilityService,
    DefaultLoanAnonymizationService anonymizationService,
    AnonymizationDueDateEvaluationService evaluationService,
    LoansForTenantFinder loansFinder) {

    final long startedAt = System.currentTimeMillis();

    // Pass 1: evaluate one page of not-yet-evaluated loans, before the drain so
    // a past-due loan is stripped in the same run. Skipped under a "never" policy.
    final CompletableFuture<Result<Optional<EvaluationStats>>> evaluation;
    if (eligibilityService.neverAnonymizeLoans()) {
      log.info("runBothPasses:: tenant never anonymizes, skipping evaluation sweep");
      evaluation = CompletableFuture.completedFuture(Result.succeeded(Optional.empty()));
    } else {
      evaluation = evaluationService.evaluateOnePage().thenApply(r -> r.map(Optional::of));
    }

    return evaluation.thenCompose(statsResult -> statsResult.after(stats ->
      // Pass 2: drain the due queue (loans are re-checked before stripping).
      anonymizationService.anonymizeLoans(loansFinder::findLoansToAnonymize)
        // Re-stamp blocked loans so they leave the due set.
        .thenCompose(r -> r.after(evaluationService::restampBlocked))
        .thenApply(r -> r.map(records -> {
          logRunMetrics(records, stats, startedAt);
          return records;
        }))));
  }

  /** One structured line per run for observability. */
  private void logRunMetrics(LoanAnonymizationRecords records,
    Optional<EvaluationStats> stats, long startedAt) {

    if (!log.isInfoEnabled()) {
      return;
    }

    final int blocked = records.getNotAnonymizedLoans().values().stream()
      .mapToInt(Collection::size).sum();

    log.info("logRunMetrics:: run finished: due={}, anonymized={}, blocked={}, "
        + "evaluated={}, stampedNever={}, stamped={}, durationMs={}",
      records.getLoansFound().size(), records.getAnonymizedLoanIds().size(), blocked,
      stats.map(EvaluationStats::evaluated).map(String::valueOf).orElse("-"),
      stats.map(EvaluationStats::stampedNever).map(String::valueOf).orElse("-"),
      stats.map(EvaluationStats::stamped).map(String::valueOf).orElse("-"),
      System.currentTimeMillis() - startedAt);
  }
}
