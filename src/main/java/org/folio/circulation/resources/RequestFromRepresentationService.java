package org.folio.circulation.resources;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.circulation.domain.RequestLevel.ITEM;
import static org.folio.circulation.domain.RequestLevel.TITLE;
import static org.folio.circulation.domain.representations.RequestProperties.INSTANCE_ID;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_LEVEL;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ATTEMPT_TO_CREATE_TLR_LINKED_TO_AN_ITEM;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSTANCE_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_HOLDINGS_RECORD_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_INSTANCE_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PICKUP_SERVICE_POINT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PROXY_RELATIONSHIP;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.NO_AVAILABLE_ITEMS_FOR_INSTANCE_ID_FOR_TLR;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.AsynchronousResult.fromFutureResult;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestLevel;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.InstanceRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;

@AllArgsConstructor
class RequestFromRepresentationService {
  private static final PageLimit LOANS_PAGE_LIMIT = limit(10000);

  private final InstanceRepository instanceRepository;
  private final ItemRepository itemRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final ConfigurationRepository configurationRepository;
  private final ProxyRelationshipValidator proxyRelationshipValidator;
  private final ServicePointPickupLocationValidator pickupLocationValidator;
  private final CirculationErrorHandler errorHandler;
  private final ItemByInstanceIdFinder itemByInstanceIdFinder;

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequestFrom(Request.Operation operation,
    JsonObject representation) {

    return initRequest(operation, representation)
      .thenApply(r -> r.next(this::validateStatus))
      .thenApply(r -> r.next(this::validateRequestLevel))
      // TODO: do we need to also check here that these IDs are valid UUIDs?
      .thenApply(this::refuseWhenNoInstanceId)
      .thenApply(this::refuseWhenNoItemId)
      .thenApply(this::refuseWhenNoHoldingsRecordId)
      .thenApply(this::refuseToCreateTlrLinkedToAnItem)
      .thenApply(r -> r.map(this::removeRelatedRecordInformation))
      .thenApply(r -> r.map(this::removeProcessingParameters))
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        Request::truncateRequestExpirationDateToTheEndOfTheDay))
      .thenComposeAsync(r -> r.after(when(
        this::shouldFetchInstance, this::fetchInstance, req -> ofAsync(() -> req))))
      .thenComposeAsync(r -> r.after(when(
        this::shouldFetchItemAndLoan, this::fetchItemAndLoan, req -> ofAsync(() -> req))))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getUser, Request::withRequester))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getProxyUser, Request::withProxy))
      .thenComposeAsync(r -> r.combineAfter(servicePointRepository::getServicePointForRequest,
        Request::withPickupServicePoint))
      .thenApply(r -> r.map(RequestAndRelatedRecords::new))
      .thenComposeAsync(r -> r.combineAfter(requestQueueRepository::get,
        RequestAndRelatedRecords::withRequestQueue))
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid)
        .thenApply(res -> errorHandler.handleValidationResult(res, INVALID_PROXY_RELATIONSHIP, r)))
      .thenApply(r -> r.next(pickupLocationValidator::refuseInvalidPickupServicePoint)
        .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_PICKUP_SERVICE_POINT, r)));
  }

  private CompletableFuture<Result<Request>> initRequest(Request.Operation operation,
    JsonObject representation) {

    return configurationRepository.lookupTlrSettings()
      .thenApply(r -> r.map(tlrSettings -> Request.from(tlrSettings, operation, representation)));
  }

  private CompletableFuture<Result<Boolean>> shouldFetchInstance(Request request) {
    return ofAsync(() -> errorHandler.hasNone(INVALID_INSTANCE_ID));
  }

  private CompletableFuture<Result<Boolean>> shouldFetchItemAndLoan(Request request) {
    return ofAsync(() -> errorHandler.hasNone(INVALID_ITEM_ID));
  }

  private CompletableFuture<Result<Request>> fetchInstance(Request request) {
    return succeeded(request)
       .combineAfter(instanceRepository::fetch, Request::withInstance);
      // TODO:  fail if instance doesn't exist
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoan(Request request) {
    if (request.isTitleLevel() && request.isPage()) {
      return fetchItemAndLoanForPageTlrRequest(request);
    }

    if (request.isTitleLevel() && request.isRecall()) {
      return fetchItemAndLoanForRecallTlrRequest(request);
    }

    return fromFutureResult(fetchItemForRequest(request))
      .flatMapFuture(this::fetchLoan)
      .flatMapFuture(this::fetchUserForLoan)
      .toCompletableFuture();
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoanForPageTlrRequest(Request request) {
    if (request.getOperation() == Request.Operation.CREATE) {
      return fromFutureResult(fetchItemForPageTlr(request)
        .thenApply(r -> r.mapFailure(err -> errorHandler.handleValidationError(err,
          NO_AVAILABLE_ITEMS_FOR_INSTANCE_ID_FOR_TLR, r))))
        // TODO: CIRC-1395 refuse when user has already requested item for the same instanceId
        .flatMapFuture(this::fetchFirstLoanForUserWithTheSameInstanceId)
        .flatMapFuture(this::fetchUserForLoan)
        .toCompletableFuture();
    } else {
      return fromFutureResult(fetchItemForRequest(request))
        .flatMapFuture(req -> succeeded(req).after(loanRepository::findOpenLoanForRequest)
          .thenApply(r -> r.map(req::withLoan)))
        .flatMapFuture(this::fetchUserForLoan)
        .toCompletableFuture();
    }
  }

  private CompletableFuture<Result<Request>> fetchItemForPageTlr(Request request) {
    return itemRepository.getFirstAvailableItemByInstanceId(request.getInstanceId())
      .thenApply(r -> r.next(item -> {
        if (item == null) {
          return failedValidation(
            "Cannot create page TLR for this instance ID - no available items found", INSTANCE_ID,
            request.getInstanceId());
        } else {
          return succeeded(request.withItem(item));
        }
      }));
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoanForRecallTlrRequest(Request request) {
    return itemByInstanceIdFinder.getItemsByInstanceId(UUID.fromString(request.getInstanceId()))
      .thenComposeAsync(r -> r.after(items -> loanRepository.findLoanWithClosestDueDate(
        mapToItemIds(items)))
        .thenApply(resultLoan -> resultLoan.map(request::withLoan))
        .thenComposeAsync(requestResult -> requestResult.combineAfter(
          record -> itemRepository.fetchFor(record.getLoan()), Request::withItem))
        .thenComposeAsync(requestResult -> requestResult.combineAfter(
          this::getUserForExistingLoan, this::addUserToLoan)))
      .thenApply(r -> errorHandler.handleValidationResult(r, INSTANCE_DOES_NOT_EXIST, request));
  }

  private List<String> mapToItemIds(Collection<Item> items) {
    return items.stream()
      .map(Item::getItemId)
      .collect(toList());
  }

  private CompletableFuture<Result<Request>> fetchLoan(Request request) {
    if (request.getTlrSettingsConfiguration().isTitleLevelRequestsFeatureEnabled()) {
      // There can be multiple loans for items of the same title, but we're only saving one of
      // them because it is enough to determine whether the patron has open loans for any
      // of the title's items

      return fetchFirstLoanForUserWithTheSameInstanceId(request)
        .thenCompose(r -> r.combineAfter(itemRepository::fetchFor, Request::withItem));
    }
    else {
      return loanRepository.findOpenLoanForRequest(request)
        .thenApply(r -> r.map(request::withLoan));
    }
  }

  private CompletableFuture<Result<Request>> fetchFirstLoanForUserWithTheSameInstanceId(
    Request request) {

    return loanRepository.findOpenLoansByUserIdWithItemAndHoldings(LOANS_PAGE_LIMIT,
        request.getUserId())
      .thenApply(r -> r.map(loans -> getLoanForItemOfTheSameInstance(request, loans)))
      .thenApply(r -> r.map(request::withLoan));
  }

  private Loan getLoanForItemOfTheSameInstance(Request request, MultipleRecords<Loan> loans) {
    return loans.getRecords().stream()
      .filter(loan -> request.getInstanceId().equals(loan.getItem().getInstanceId()))
      .findFirst()
      .orElse(null);
  }

  private CompletableFuture<Result<User>> getUserForExistingLoan(Request request) {
    Loan loan = request.getLoan();

    if (loan == null) {
      return ofAsync(() -> null);
    }

    return userRepository.getUser(loan.getUserId());
  }

  private Request addUserToLoan(Request request, User user) {
    if (request.getLoan() == null) {
      return request;
    }
    return request.withLoan(request.getLoan().withUser(user));
  }

  private Result<Request> validateStatus(Request request) {
    JsonObject representation = request.getRequestRepresentation();
    RequestStatus status = RequestStatus.from(representation);

    if (!status.isValid()) {
      return failed(new BadRequestFailure(RequestStatus.invalidStatusErrorMessage()));
    }
    else {
      status.writeTo(representation);
      return succeeded(request);
    }
  }

  private Result<Request> validateRequestLevel(Request request) {
    JsonObject representation = request.getRequestRepresentation();

    String requestLevelRaw = representation.getString(REQUEST_LEVEL);
    RequestLevel requestLevel = RequestLevel.from(requestLevelRaw);
    boolean tlrEnabled = request.getTlrSettingsConfiguration().isTitleLevelRequestsFeatureEnabled();

    List<RequestLevel> allowedStatuses = tlrEnabled
      ? List.of(ITEM, TITLE)
      : List.of(ITEM);

    if (!allowedStatuses.contains(requestLevel)) {
      String allowedStatusesJoined = allowedStatuses.stream()
        .map(existingLevel -> StringUtils.wrap(existingLevel.getValue(), '"'))
        .collect(Collectors.joining(", "));

      return failedValidation(
        "requestLevel must be one of the following: " + allowedStatusesJoined, "requestLevel",
        requestLevelRaw);
    }

    return succeeded(request);
  }

  private Result<Request> refuseWhenNoInstanceId(Result<Request> result) {
    return result.next(this::validateInstanceIdPresence)
      .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_INSTANCE_ID, result));
  }

  private Result<Request> validateInstanceIdPresence(Request context) {
    JsonObject representation = context.getRequestRepresentation();
    String instanceId = getProperty(representation, INSTANCE_ID);

    if (isBlank(instanceId)) {
      return failedValidation("Cannot create a request with no instance ID", "instanceId",
        instanceId);
    }
    else {
      return of(() -> context);
    }
  }

  private Result<Request> refuseWhenNoItemId(Result<Request> request) {
    return request.next(this::validateItemIdPresence)
      .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_ITEM_ID, request));
  }

  private Result<Request> validateItemIdPresence(Request request) {
    String itemId = request.getItemId();

    if (request.getRequestLevel() == ITEM && isBlank(itemId)) {
      return failedValidation("Cannot create an item level request with no item ID",
        "itemId", itemId);
    }
    else {
      return of(() -> request);
    }
  }

  private Result<Request> refuseWhenNoHoldingsRecordId(Result<Request> request) {
    return request.next(this::validateHoldingsRecordIdPresence)
      .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_HOLDINGS_RECORD_ID, request));
  }

  private Result<Request> validateHoldingsRecordIdPresence(Request request) {
    String holdingsRecordId = request.getHoldingsRecordId();

    if (errorHandler.hasNone(INVALID_ITEM_ID)
      && isNotBlank(request.getItemId())
      && isBlank(holdingsRecordId)) {

      return failedValidation("Cannot create a request with item ID but no holdings record ID",
        "holdingsRecordId", holdingsRecordId);
    }
    else {
      return of(() -> request);
    }
  }

  private Result<Request> refuseToCreateTlrLinkedToAnItem(Result<Request> request) {
    return request.next(this::validateAbsenceOfItemLinkInTlr)
      .mapFailure(err -> errorHandler.handleValidationError(err,
        ATTEMPT_TO_CREATE_TLR_LINKED_TO_AN_ITEM, request));
  }

  private Result<Request> validateAbsenceOfItemLinkInTlr(Request request) {
    String itemId = request.getItemId();
    String holdingsRecordId = request.getHoldingsRecordId();

    if (errorHandler.hasNone(INVALID_ITEM_ID, INVALID_HOLDINGS_RECORD_ID)
      && request.getOperation() == Request.Operation.CREATE
      && request.getRequestLevel() == TITLE
      && (isNotBlank(itemId) || isNotBlank(holdingsRecordId))) {

      Map<String, String> errorParameters = new HashMap<>();
      errorParameters.put("itemId", itemId);
      errorParameters.put("holdingsRecordId", holdingsRecordId);
      return failedValidation("Attempt to create TLR request linked to an item",
        errorParameters);
    }
    else {
      return of(() -> request);
    }
  }

  private Request removeRelatedRecordInformation(Request request) {
    JsonObject representation = request.getRequestRepresentation();

    representation.remove("item");
    representation.remove("requester");
    representation.remove("proxy");
    representation.remove("loan");
    representation.remove("pickupServicePoint");
    representation.remove("deliveryAddress");

    return request;
  }

  private Request removeProcessingParameters(Request request) {
    JsonObject representation = request.getRequestRepresentation();

    representation.remove("requestProcessingParameters");

    return request;
  }

  private CompletableFuture<Result<Request>> fetchItemForRequest(Request request) {
    return succeeded(request)
      .combineAfter(itemRepository::fetchFor, Request::withItem);
  }

  private CompletableFuture<Result<Request>> fetchUserForLoan(Request request) {
    return succeeded(request)
        .combineAfter(this::getUserForExistingLoan, this::addUserToLoan);
  }

  enum Operation {
    CREATION, REPLACEMENT;

    public boolean isCreation(){
      return this == CREATION;
    }
  }
}
