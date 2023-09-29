package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.representations.AddInfoRequest;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.server.NoContentResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.domain.LoanAction.PATRON_INFO_ADDED;
import static org.folio.circulation.domain.LoanAction.STAFF_INFO_ADDED;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION_COMMENT;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.MappingFunctions.toFixedValue;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

public class AddInfoResource extends Resource {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public AddInfoResource(HttpClient client) {
    super(client);
  }

  @Override
  public void register(Router router) {
    new RouteRegistration("/circulation/loans/:id/add-info", router)
      .create(this::addPatronOrStaffInfo);
  }

  private void addPatronOrStaffInfo(RoutingContext routingContext) {
    log.debug("addPatronOrStaffInfo:: invoked");
    final WebContext context = new WebContext(routingContext);
    createAddInfoRequest(routingContext)
      .after(r -> processInfoAdded(r, routingContext))
      .thenApply(r -> r.map(toFixedValue(NoContentResponse::noContent)))
      .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> processInfoAdded(
    final AddInfoRequest request, RoutingContext routingContext) {
    log.debug("processInfoAdded:: parameters request: {}", request);

    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final var itemRepository = new ItemRepository(clients);
    final var userRepository = new UserRepository(clients);
    final var loanRepository = new LoanRepository(clients, itemRepository, userRepository);

    final EventPublisher eventPublisher = new EventPublisher(routingContext);

    log.info("starting add-info process for loan {}", request.getLoanId());
    return succeeded(request)
      .after(r -> getExistingLoan(loanRepository, r))
      .thenApply(this::toLoanAndRelatedRecords)
      .thenApply(r -> addPatronOrStaffInfo(r, request))
      .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
      .thenComposeAsync(r -> r.after(eventPublisher::publishInfoAddedEvent));
  }

  private Result<AddInfoRequest> createAddInfoRequest(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.body().asJsonObject();

    if (!staffOrPatronInfoProvidedInRequest(body)) {
      log.warn("createAddInfoRequest:: staff or patron info is not provided in request");
      return failed(singleValidationError(
        String.format(
          "An action of type %s or %s, with associated actionComment, required in order to add info",
          PATRON_INFO_ADDED.getValue(),
          STAFF_INFO_ADDED.getValue()), ACTION, null));
    }
    return Result.of(() -> new AddInfoRequest(loanId, body.getString(ACTION), body.getString(ACTION_COMMENT)));
  }

  private boolean staffOrPatronInfoProvidedInRequest(JsonObject requestBody) {
    log.debug("staffOrPatronInfoProvidedInRequest:: parameters requestBody: {}", () -> requestBody);
    String action = requestBody.getString(ACTION);
    return StringUtils.isNotBlank (action)
      && StringUtils.isNotBlank(requestBody.getString(ACTION_COMMENT))
      && (action.equals(PATRON_INFO_ADDED.getValue()) || action.equals(STAFF_INFO_ADDED.getValue()));
  }

  CompletableFuture<Result<Loan>> getExistingLoan(LoanRepository loanRepository,
    AddInfoRequest addInfoRequest) {

    return loanRepository.getById(addInfoRequest.getLoanId());
  }

  private Result<LoanAndRelatedRecords> addPatronOrStaffInfo(
    Result<LoanAndRelatedRecords> loanResult, AddInfoRequest request) {

    return loanResult.map(l -> addPatronOrStaffInfo(l, request.getAction(),
      request.getActionComment()));
  }

  private LoanAndRelatedRecords addPatronOrStaffInfo(LoanAndRelatedRecords loanAndRelatedRecords,
    String action, String actionComment) {

    log.debug("addPatronOrStaffInfo:: parameters loanAndRelatedRecords: {}, action: {}, " +
      "actionComment: {}", () -> loanAndRelatedRecords, () -> action, () -> actionComment);
    loanAndRelatedRecords.getLoan().changeAction(action);
    loanAndRelatedRecords.getLoan().changeActionComment(actionComment);

    return loanAndRelatedRecords;
  }

  private Result<LoanAndRelatedRecords> toLoanAndRelatedRecords(Result<Loan> loanResult) {
    return loanResult.next(loan -> succeeded(new LoanAndRelatedRecords(loan)));
  }

}
