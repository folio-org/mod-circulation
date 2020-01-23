package org.folio.circulation.resources;

import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ConfigurationRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeService;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.representations.LoanResponse;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class RenewalResource extends Resource {

  private final String rootPath;
  private final RenewalStrategy renewalStrategy;

  RenewalResource(String rootPath, RenewalStrategy renewalStrategy, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
    this.renewalStrategy = renewalStrategy;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      rootPath, router);

    routeRegistration.create(this::renew);
  }

  private void renew(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final UserRepository userRepository = new UserRepository(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final ConfigurationRepository configurationRepository = new ConfigurationRepository(clients);
    final DueDateScheduledNoticeService scheduledNoticeService = DueDateScheduledNoticeService.using(clients);

    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients);

    //TODO: Validation check for same user should be in the domain service

    JsonObject bodyAsJson = routingContext.getBodyAsJson();
    CompletableFuture<Result<Loan>> findLoanResult = findLoan(bodyAsJson,
      loanRepository,
      itemRepository,
      userRepository);

    findLoanResult
      .thenApply(r -> r.map(LoanAndRelatedRecords::new))
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        LoanAndRelatedRecords::withTimeZone))
      .thenComposeAsync(r -> r.after(records -> renewalStrategy.renew(records, bodyAsJson, clients)))
      .thenComposeAsync(r -> r.after(relatedRecords -> updateLoanAndItemInStorage(relatedRecords,
        itemRepository, loanRepository)))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenApply(r -> r.next(loanNoticeSender::sendRenewalPatronNotice))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(LoanResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> updateLoanAndItemInStorage(
    LoanAndRelatedRecords relatedRecords, ItemRepository itemRepository,
    LoanRepository loanRepository) {

    Loan loan = relatedRecords.getLoan();
    if (loan == null || loan.getItem() == null) {
      return null;
    }

    return itemRepository.updateItem(loan.getItem())
      .thenCompose(x -> loanRepository.updateLoan(loan))
      .thenApply(mapResult(relatedRecords::withLoan));
  }

  protected abstract CompletableFuture<Result<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository);
}
