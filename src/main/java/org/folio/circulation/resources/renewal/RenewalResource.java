package org.folio.circulation.resources.renewal;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.StoreLoanAndItem;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.FeeFineScheduledNoticeService;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.domain.validation.AutomatedPatronBlocksValidator;
import org.folio.circulation.resources.LoanNoticeSender;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.http.server.WebContext;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public abstract class RenewalResource extends Resource {
  private final String rootPath;
  private final RenewalStrategy renewalStrategy;
  private final RenewalFeeProcessingStrategy feeProcessing;

  RenewalResource(String rootPath, RenewalStrategy renewalStrategy,
    RenewalFeeProcessingStrategy feeProcessing, HttpClient client) {

    super(client);
    this.rootPath = rootPath;
    this.renewalStrategy = renewalStrategy;
    this.feeProcessing = feeProcessing;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      rootPath, router);

    routeRegistration.create(this::renew);
  }

  private void renew(RoutingContext routingContext) {
    final WebContext webContext = new WebContext(routingContext);
    final Clients clients = Clients.create(routingContext, client);

    final LoanRepository loanRepository = new LoanRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final UserRepository userRepository = new UserRepository(clients);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final StoreLoanAndItem storeLoanAndItem = new StoreLoanAndItem(loanRepository, itemRepository);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final ConfigurationRepository configurationRepository = new ConfigurationRepository(clients);
    final DueDateScheduledNoticeService scheduledNoticeService = DueDateScheduledNoticeService.using(clients);

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients);

    final AutomatedPatronBlocksRepository automatedPatronBlocksRepository =
      new AutomatedPatronBlocksRepository(clients);
    final AutomatedPatronBlocksValidator automatedPatronBlocksValidator =
      new AutomatedPatronBlocksValidator(automatedPatronBlocksRepository,
        messages -> new ValidationErrorFailure(messages.stream()
          .map(message -> new ValidationError(message, new HashMap<>()))
          .collect(Collectors.toList())));
    final FeeFineScheduledNoticeService feeFineNoticesService =
      FeeFineScheduledNoticeService.using(clients);

    //TODO: Validation check for same user should be in the domain service
    JsonObject bodyAsJson = routingContext.getBodyAsJson();
    CompletableFuture<Result<Loan>> findLoanResult = findLoan(bodyAsJson,
      loanRepository, itemRepository, userRepository);

    findLoanResult
      .thenApply(r -> r.map(loan -> RenewalContext.create(loan, bodyAsJson, webContext.getUserId())))
      .thenComposeAsync(r -> r.after(
        automatedPatronBlocksValidator::refuseWhenRenewalActionIsBlockedForPatron))
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        RenewalContext::withTimeZone))
      .thenComposeAsync(r -> r.after(context -> renewalStrategy.renew(context, clients)))
      .thenComposeAsync(r -> r.after(storeLoanAndItem::updateLoanAndItemInStorage))
      .thenComposeAsync(r -> r.after(context -> feeProcessing.processFeesFines(context, clients)))
      .thenApplyAsync(r -> r.next(feeFineNoticesService::scheduleOverdueFineNotices))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDueDateChangedEvent))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenApply(r -> r.next(loanNoticeSender::sendRenewalPatronNotice))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(r -> r.map(this::toResponse))
      .thenAccept(webContext::writeResultToHttpResponse);
  }

  private HttpResponse toResponse(JsonObject body) {
    return JsonHttpResponse.ok(body,
      String.format("/circulation/loans/%s", body.getString("id")));
  }

  protected abstract CompletableFuture<Result<Loan>> findLoan(JsonObject request,
    LoanRepository loanRepository, ItemRepository itemRepository,
    UserRepository userRepository);
}
