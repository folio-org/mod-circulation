package org.folio.circulation.resources;

import org.folio.circulation.infrastructure.storage.ActualCostRecordRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.CloseLoanWithLostItemService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.services.actualcostrecord.ActualCostRecordExpirationService;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.fetching.PageableFetcher;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
public class ExpiredActualCostProcessingResource extends Resource {
  public ExpiredActualCostProcessingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/actual-cost-expiration-by-timeout", router)
      .create(this::process);
  }

  private void process(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final var clients = create(context, client);

    final var eventPublisher = new EventPublisher(routingContext);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var closeLoanWithLostItemService = new CloseLoanWithLostItemService(loanRepository,
      itemRepository, new AccountRepository(clients), new LostItemPolicyRepository(clients),
      eventPublisher, new ActualCostRecordRepository(clients));
    final var loanPageableFetcher = new PageableFetcher<>(loanRepository);
    final var actualCostRecordExpirationService = new ActualCostRecordExpirationService(
      loanPageableFetcher, closeLoanWithLostItemService, itemRepository);

    actualCostRecordExpirationService.expireActualCostRecords()
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }


}
