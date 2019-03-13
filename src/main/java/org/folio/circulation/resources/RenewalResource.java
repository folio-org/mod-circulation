package org.folio.circulation.resources;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.applyCLDDMForLoanAndRelatedRecords;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRenewalService;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.domain.representations.LoanResponse;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class RenewalResource extends Resource {
  private final String rootPath;

  RenewalResource(HttpClient client, String rootPath) {
    super(client);
    this.rootPath = rootPath;
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
    final ItemRepository itemRepository = new ItemRepository(clients, true, true);
    final UserRepository userRepository = new UserRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final LoanRenewalService loanRenewalService = LoanRenewalService.using(clients);
    final ClosedLibraryStrategyService strategyService =
      ClosedLibraryStrategyService.using(clients, DateTime.now(DateTimeZone.UTC), true);


    //TODO: Validation check for same user should be in the domain service

    findLoan(routingContext.getBodyAsJson(), loanRepository, itemRepository, userRepository)
      .thenApply(r -> r.map(LoanAndRelatedRecords::new))
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenApply(r -> r.next(loanRenewalService::renew))
      .thenComposeAsync(r -> r.after(records -> applyCLDDMForLoanAndRelatedRecords(strategyService, records)))
      .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(LoanResponse::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  protected abstract CompletableFuture<HttpResult<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository);
}
