package org.folio.circulation.resources;

import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ExpiredActualCostProcessingResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public ExpiredActualCostProcessingResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/actual-cost-expiration-by-timeout", router)
      .create(this::process);
  }

  private void process(RoutingContext routingContext) {
    log.debug("process:: expiring actual cost records by timeout");
    var context = new WebContext(routingContext);
    var clients = create(context, client);

    var eventPublisher = new EventPublisher(routingContext);
    var itemRepository = new ItemRepository(clients);
    var userRepository = new UserRepository(clients);
    var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    var accountRepository = new AccountRepository(clients);
    var lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    var actualCostRecordRepository = new ActualCostRecordRepository(clients);
    var closeLoanWithLostItemService = new CloseLoanWithLostItemService(loanRepository,
      itemRepository, accountRepository, lostItemPolicyRepository,
      eventPublisher, actualCostRecordRepository);
    var actualCostRecordExpirationService = new ActualCostRecordExpirationService(
      closeLoanWithLostItemService, itemRepository, actualCostRecordRepository,
      loanRepository);

    actualCostRecordExpirationService.expireActualCostRecords()
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }
}
