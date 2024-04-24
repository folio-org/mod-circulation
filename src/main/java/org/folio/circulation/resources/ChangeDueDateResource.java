package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.ChangeDueDateRequest.DUE_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FIND_SINGLE_OPEN_LOAN;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_DOES_NOT_EXIST;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.notice.schedule.LoanScheduledNoticeService;
import org.folio.circulation.domain.notice.schedule.ReminderFeeScheduledNoticeService;
import org.folio.circulation.domain.representations.ChangeDueDateRequest;
import org.folio.circulation.domain.validation.ItemStatusValidator;
import org.folio.circulation.domain.validation.LoanValidator;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ChangeDueDateResource extends Resource {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public ChangeDueDateResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/loans/:id/change-due-date", router)
      .create(this::changeDueDate);
  }

  private void changeDueDate(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    createChangeDueDateRequest(routingContext)
      .after(r -> processChangeDueDate(r, routingContext))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> processChangeDueDate(
    final ChangeDueDateRequest request, RoutingContext routingContext) {

    log.debug("processChangeDueDate:: parameters request: {}", () -> request);

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    final var settingsRepository = new SettingsRepository(clients);

    final WebContext webContext = new WebContext(routingContext);
    final OkapiPermissions okapiPermissions = OkapiPermissions.from(webContext.getHeaders());

    final CirculationErrorHandler errorHandler = new OverridingErrorHandler(okapiPermissions);

    final var requestRepository = RequestRepository.using(clients,
      itemRepository, userRepository, loanRepository);
    final var requestQueueRepository = new RequestQueueRepository(requestRepository);

    final LoanScheduledNoticeService scheduledNoticeService
      = LoanScheduledNoticeService.using(clients);
    final OverdueFinePolicyRepository overdueFinePolicyRepository =
      new OverdueFinePolicyRepository(clients);
    final ReminderFeeScheduledNoticeService scheduledRemindersService =
      new ReminderFeeScheduledNoticeService(clients);

    final ItemStatusValidator itemStatusValidator = new ItemStatusValidator(
        ChangeDueDateResource::errorWhenInIncorrectStatus);

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients, loanRepository);

    log.info("starting change due date process for loan {}", request.getLoanId());
    return succeeded(request)
      .after(r -> getExistingLoan(loanRepository, r))
      .thenApply(LoanValidator::refuseWhenLoanIsClosed)
      .thenApply(this::toLoanAndRelatedRecords)
      .thenComposeAsync(r -> r.combineAfter(settingsRepository::lookupTlrSettings,
        LoanAndRelatedRecords::withTlrSettings))
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenApply(itemStatusValidator::refuseWhenItemStatusDoesNotAllowDueDateChange)
      .thenCompose(r -> r.after(ctx -> lookupOverdueFinePolicy(ctx, overdueFinePolicyRepository, errorHandler)))
      .thenApply(r -> changeDueDate(r, request))
      .thenApply(r -> r.map(this::unsetDueDateChangedByRecallIfNoOpenRecallsInQueue))
      .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
      .thenComposeAsync(r -> r.after(eventPublisher::publishDueDateChangedEvent))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenApply(r -> r.next(scheduledRemindersService::rescheduleFirstReminder))
      .thenCompose(r -> r.after(loanNoticeSender::sendManualDueDateChangeNotice));
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> lookupOverdueFinePolicy(
    LoanAndRelatedRecords context, OverdueFinePolicyRepository overdueFinePolicyRepository,
    CirculationErrorHandler errorHandler)
  {
    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
      FAILED_TO_FETCH_USER)) {
      return completedFuture(succeeded(context));
    }

    return overdueFinePolicyRepository
      .findOverdueFinePolicyForLoan(succeeded(context.getLoan()))
      .thenApply(mapResult(context::withLoan));
  }

  private LoanAndRelatedRecords unsetDueDateChangedByRecallIfNoOpenRecallsInQueue(
      LoanAndRelatedRecords loanAndRelatedRecords) {

    log.debug("unsetDueDateChangedByRecallIfNoOpenRecallsInQueue:: parameters loanAndRelatedRecords: {}",
      () -> loanAndRelatedRecords);
    RequestQueue queue = loanAndRelatedRecords.getRequestQueue();
    Loan loan = loanAndRelatedRecords.getLoan();
    log.info("Loan {} prior to flag check: {}", loan.getId(), loan.asJson().toString());
    if (loan.wasDueDateChangedByRecall() && !queue.hasOpenRecalls()) {
      log.info("Loan {} registers as having due date change flag set to true and no open recalls in queue.", loan.getId());
      return loanAndRelatedRecords.withLoan(loan.unsetDueDateChangedByRecall());
    } else {
      log.info("Loan {} registers as either not having due date change flag set to true or as having open recalls in queue.", loan.getId());
      return loanAndRelatedRecords;
    }
  }

  CompletableFuture<Result<Loan>> getExistingLoan(LoanRepository loanRepository,
    ChangeDueDateRequest changeDueDateRequest) {

    log.debug("getExistingLoan:: parameters changeDueDateRequest: {}", () -> changeDueDateRequest);

    return loanRepository.getById(changeDueDateRequest.getLoanId())
      .thenApplyAsync(r -> r.map(exitingLoan -> exitingLoan.setPreviousDueDate(
        exitingLoan.getDueDate())));
  }

  private Result<LoanAndRelatedRecords> changeDueDate(Result<LoanAndRelatedRecords> loanResult,
      ChangeDueDateRequest request) {

    log.debug("changeDueDate:: parameters request: {}", () -> request);

    return loanResult.map(l -> changeDueDate(l, request.getDueDate()));
  }

  private LoanAndRelatedRecords changeDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
    ZonedDateTime dueDate) {

    log.debug("changeDueDate:: parameters loanAndRelatedRecords: {}, dueDate: {}",
      () -> loanAndRelatedRecords, () -> dueDate);
    loanAndRelatedRecords.getLoan().changeDueDate(dueDate).resetReminders();

    return loanAndRelatedRecords;
  }

  private Result<ChangeDueDateRequest> createChangeDueDateRequest(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();
    log.debug("createChangeDueDateRequest:: parameters loanId: {}, body: {}", () -> loanId, () -> body);

    if (!body.containsKey(DUE_DATE)) {
      log.warn("createChangeDueDateRequest:: the request does not contain dueDate");
      return failed(singleValidationError(
        "A new due date is required in order to change the due date", DUE_DATE, null));
    }

    return Result.of(() -> new ChangeDueDateRequest(loanId, getDateTimeProperty(body, DUE_DATE)));
  }

  private Result<LoanAndRelatedRecords> toLoanAndRelatedRecords(Result<Loan> loanResult) {
    return loanResult.next(loan -> succeeded(new LoanAndRelatedRecords(loan)));
  }

  private static ValidationErrorFailure errorWhenInIncorrectStatus(Item item) {
    String message = String.format("item is %s", item.getStatusName());

    return singleValidationError(message, ITEM_ID, item.getItemId());
  }
}
