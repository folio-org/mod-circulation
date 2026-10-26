package org.folio.circulation.resources;

import static org.folio.circulation.domain.anonymization.service.AnonymizationCandidatesFinder.AS_EARLY_AS_POSSIBLE;
import static org.folio.circulation.domain.anonymization.service.AnonymizationCandidatesFinder.LIBRARY_POLICY;
import static org.folio.circulation.support.results.AsynchronousResultBindings.safelyInitialise;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.Environment;
import org.folio.circulation.domain.anonymization.DefaultLoanAnonymizationService;
import org.folio.circulation.domain.anonymization.LoanAnonymizationRecords;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.anonymization.service.AnonymizationCandidatesFinder;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersService;
import org.folio.circulation.domain.anonymization.service.LoansForTenantFinder;
import org.folio.circulation.domain.representations.anonymization.AnonymizeLoansRepresentation;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
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
 * Perform automatic loan anonymization based on tenant settings for loan history.
 * This process is intended to run in short intervals.
 *
 */
public class ScheduledAnonymizationProcessingResource extends Resource {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  // Maximum limit of GET /loan-storage/anonymization-candidates
  private static final int MAX_ANONYMIZATION_CANDIDATES = 50000;

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

    CirculationSettingsService circulationSettingsService = new CirculationSettingsService(clients);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var accountRepository = new AccountRepository(clients);

    final var anonymizeStorageLoansRepository = new AnonymizeStorageLoansRepository(clients);
    final var eventPublisher = new EventPublisher(clients);

    final var loansFinder = new LoansForTenantFinder(loanRepository, accountRepository,
      Environment.getScheduledAnonymizationNumberOfLoansToCheck());
    final var candidatesFinder = new AnonymizationCandidatesFinder(loanRepository,
      accountRepository, ClockUtil::getZonedDateTime);

    log.info("Initializing loan anonymization for current tenant");

    safelyInitialise(circulationSettingsService::getLoanAnonymizationSettings)
      .thenCompose(r -> r.after(config -> config.isUserSpecificAnonymizationEnabled()
        ? anonymizeLoansByPreference(config, candidatesFinder,
            anonymizeStorageLoansRepository, eventPublisher)
        : anonymizationService(config, anonymizeStorageLoansRepository, eventPublisher)
            .anonymizeLoans(loansFinder::findLoansToAnonymize)))
      .thenApply(AnonymizeLoansRepresentation::from)
      .thenApply(r -> r.map(JsonHttpResponse::ok))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenAccept(context::writeResultToHttpResponse);
  }

  /**
   * One run per preference group, each with the policy of its group: "as early as possible"
   * first with at most half of the loans to check, then the library policy with the rest.
   * Loans of users who chose "never" are never fetched.
   */
  private CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoansByPreference(
    LoanAnonymizationConfiguration config, AnonymizationCandidatesFinder finder,
    AnonymizeStorageLoansRepository anonymizeStorageLoansRepository,
    EventPublisher eventPublisher) {

    log.info("anonymizeLoansByPreference:: user-specific loan anonymization is {}",
      config.getUserSpecificClosingType());

    final int numberOfLoansToCheck = Math.min(MAX_ANONYMIZATION_CANDIDATES,
      Environment.getScheduledAnonymizationNumberOfLoansToCheck());
    final var asEarlyAsPossibleConfig = config.forAsEarlyAsPossible();

    return anonymizationService(asEarlyAsPossibleConfig, anonymizeStorageLoansRepository,
        eventPublisher)
      .anonymizeLoans(() -> finder.findLoansToAnonymize(AS_EARLY_AS_POSSIBLE,
        asEarlyAsPossibleConfig, (numberOfLoansToCheck + 1) / 2))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenCompose(asEarlyAsPossible -> {
        if (asEarlyAsPossible.failed()) {
          log.error("anonymizeLoansByPreference:: anonymization of {} failed, continuing " +
            "with {}: {}", AS_EARLY_AS_POSSIBLE, LIBRARY_POLICY, asEarlyAsPossible.cause());
        }
        final int loansFound = asEarlyAsPossible
          .map(records -> records.getLoansFound().size())
          .orElse(0);

        return anonymizationService(config, anonymizeStorageLoansRepository, eventPublisher)
          .anonymizeLoans(() -> finder.findLoansToAnonymize(LIBRARY_POLICY, config,
            numberOfLoansToCheck - loansFound))
          .thenApply(libraryPolicy -> asEarlyAsPossible.combine(libraryPolicy,
            LoanAnonymizationRecords::merge));
      });
  }

  private static DefaultLoanAnonymizationService anonymizationService(
    LoanAnonymizationConfiguration config,
    AnonymizeStorageLoansRepository anonymizeStorageLoansRepository,
    EventPublisher eventPublisher) {

    return new DefaultLoanAnonymizationService(
      new AnonymizationCheckersService(config, ClockUtil::getZonedDateTime),
      anonymizeStorageLoansRepository, eventPublisher);
  }
}
