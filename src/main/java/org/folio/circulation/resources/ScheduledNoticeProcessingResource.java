package org.folio.circulation.resources;

import static org.folio.circulation.support.results.AsynchronousResultBindings.safelyInitialise;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.notices.ScheduledNoticesRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class ScheduledNoticeProcessingResource extends Resource {
  private final String rootPath;

  ScheduledNoticeProcessingResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);

    routeRegistration.create(this::process);
  }

  private void process(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final ScheduledNoticesRepository scheduledNoticesRepository =
      ScheduledNoticesRepository.using(clients);
    final ConfigurationRepository configurationRepository =
      new ConfigurationRepository(clients);
    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var requestRepository = RequestRepository.using(clients,
      itemRepository, userRepository, loanRepository);

    safelyInitialise(configurationRepository::lookupSchedulerNoticesProcessingLimit)
      .thenCompose(r -> r.after(limit -> findNoticesToSend(configurationRepository,
        scheduledNoticesRepository, limit)))
      .thenCompose(r -> r.after(notices -> handleNotices(clients, requestRepository,
        loanRepository, notices)))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .exceptionally(CommonFailures::failedDueToServerError)
      .thenAccept(context::writeResultToHttpResponse);
  }

  protected abstract CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> findNoticesToSend(
    ConfigurationRepository configurationRepository,
    ScheduledNoticesRepository scheduledNoticesRepository, PageLimit pageLimit);

  protected abstract CompletableFuture<Result<MultipleRecords<ScheduledNotice>>> handleNotices(
    Clients clients,
    RequestRepository requestRepository,
    LoanRepository loanRepository,
    MultipleRecords<ScheduledNotice> noticesResult);
}
