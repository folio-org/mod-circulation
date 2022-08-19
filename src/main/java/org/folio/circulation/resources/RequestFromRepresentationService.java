package org.folio.circulation.resources;

import static java.lang.String.join;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.circulation.domain.ItemStatus.AWAITING_DELIVERY;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.ItemStatus.PAGED;
import static org.folio.circulation.domain.RequestLevel.ITEM;
import static org.folio.circulation.domain.RequestLevel.TITLE;
import static org.folio.circulation.domain.representations.RequestProperties.HOLDINGS_RECORD_ID;
import static org.folio.circulation.domain.representations.RequestProperties.INSTANCE_ID;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_ID;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_DATE;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_LEVEL;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ATTEMPT_TO_CREATE_TLR_LINKED_TO_AN_ITEM;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INSTANCE_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_HOLDINGS_RECORD_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_INSTANCE_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PICKUP_SERVICE_POINT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PROXY_RELATIONSHIP;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.NO_AVAILABLE_ITEMS_FOR_TLR;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.TLR_RECALL_WITHOUT_OPEN_LOAN_OR_RECALLABLE_ITEM;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.copyProperty;
import static org.folio.circulation.support.results.AsynchronousResult.fromFutureResult;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestFulfilmentPreference;
import org.folio.circulation.domain.RequestLevel;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.inventory.HoldingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.InstanceRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.services.ItemForPageTlrService;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.request.RequestRelatedRepositories;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

class RequestFromRepresentationService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final PageLimit LOANS_PAGE_LIMIT = limit(10000);
  private static final Set<ItemStatus> RECALLABLE_ITEM_STATUSES =
    Set.of(PAGED, AWAITING_PICKUP, AWAITING_DELIVERY);
  private final Request.Operation operation;
  private final InstanceRepository instanceRepository;
  private final ItemRepository itemRepository;
  private final HoldingsRepository holdingsRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final ConfigurationRepository configurationRepository;
  private final ProxyRelationshipValidator proxyRelationshipValidator;
  private final ServicePointPickupLocationValidator pickupLocationValidator;
  private final CirculationErrorHandler errorHandler;
  private final ItemByInstanceIdFinder itemByInstanceIdFinder;
  private final ItemForPageTlrService itemForPageTlrService;

  public RequestFromRepresentationService(Request.Operation operation,
    RequestRelatedRepositories repositories, ProxyRelationshipValidator proxyRelationshipValidator,
    ServicePointPickupLocationValidator pickupLocationValidator,
    CirculationErrorHandler errorHandler, ItemByInstanceIdFinder itemByInstanceIdFinder,
    ItemForPageTlrService itemForPageTlrService) {

    this.operation = operation;

    this.instanceRepository = repositories.getInstanceRepository();
    this.itemRepository = repositories.getItemRepository();
    this.holdingsRepository = repositories.getHoldingsRepository();
    this.requestQueueRepository = repositories.getRequestQueueRepository();
    this.userRepository = repositories.getUserRepository();
    this.loanRepository = repositories.getLoanRepository();
    this.servicePointRepository = repositories.getServicePointRepository();
    this.configurationRepository = repositories.getConfigurationRepository();

    this.proxyRelationshipValidator = proxyRelationshipValidator;
    this.pickupLocationValidator = pickupLocationValidator;
    this.errorHandler = errorHandler;
    this.itemByInstanceIdFinder = itemByInstanceIdFinder;
    this.itemForPageTlrService = itemForPageTlrService;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequestFrom(JsonObject representation) {

    return configurationRepository.lookupTlrSettings()
      .thenCompose(r -> r.after(tlrSettings -> initRequest(operation, tlrSettings, representation)))
      .thenApply(r -> r.next(this::validateStatus))
      .thenApply(r -> r.next(this::validateRequestLevel))
      .thenApply(r -> r.next(this::validateFulfilmentPreference))
      // TODO: do we need to also check here that these IDs are valid UUIDs?
      .thenApply(this::refuseWhenNoInstanceId)
      .thenApply(this::refuseWhenNoItemId)
      .thenApply(this::refuseWhenNoHoldingsRecordId)
      .thenApply(this::refuseToCreateTlrLinkedToAnItem)
      .thenApply(this::refuseWhenNoRequestDate)
      .thenApply(r -> r.map(this::removeRelatedRecordInformation))
      .thenApply(r -> r.map(this::removeProcessingParameters))
      .thenCompose(r -> r.combineAfter(configurationRepository::findTimeZoneConfiguration,
        Request::truncateRequestExpirationDateToTheEndOfTheDay))
      .thenComposeAsync(r -> r.after(when(
        this::shouldFetchInstance, this::fetchInstance, req -> ofAsync(() -> req))))
      .thenComposeAsync(r -> r.after(when(
        this::shouldFetchInstanceItems, this::findInstanceItems, req -> ofAsync(() -> req))))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getUser, Request::withRequester))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getProxyUser, Request::withProxy))
      .thenComposeAsync(r -> r.combineAfter(servicePointRepository::getServicePointForRequest,
        Request::withPickupServicePoint))
      .thenApply(r -> r.map(RequestAndRelatedRecords::new))
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenComposeAsync(r -> r.after(when(
        this::shouldFetchItemAndLoan, this::fetchItemAndLoan, records -> ofAsync(() -> records))))
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid)
        .thenApply(res -> errorHandler.handleValidationResult(res, INVALID_PROXY_RELATIONSHIP, r)))
      .thenApply(r -> r.next(pickupLocationValidator::refuseInvalidPickupServicePoint)
        .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_PICKUP_SERVICE_POINT, r)));
  }

  private CompletableFuture<Result<Request>> initRequest(Request.Operation operation,
    TlrSettingsConfiguration tlrSettings, JsonObject representation) {

    return fillInMissingProperties(representation, operation)
      .thenApply(r -> r.map(request -> Request.from(tlrSettings, operation, request)));
  }

  // this is to provide backward compatibility to third-party clients who use pre-TLR request schema
  private CompletableFuture<Result<JsonObject>> fillInMissingProperties(JsonObject request,
    Request.Operation operation) {

    List<String> newProperties = List.of(REQUEST_LEVEL, HOLDINGS_RECORD_ID, INSTANCE_ID);
    boolean requestContainsNewProperties = request.getMap()
      .keySet()
      .stream()
      .anyMatch(newProperties::contains);

    if (operation != Request.Operation.CREATE || requestContainsNewProperties) {
      return ofAsync(() -> request);
    }

    log.warn("Request properties {} are missing, assuming item-level request by a legacy client",
      newProperties);
    request.put(REQUEST_LEVEL, ITEM.getValue());

    return fillInMissingHoldingsRecordId(request)
      .thenCompose(r -> r.after(this::fillInMissingInstanceId));
  }

  private CompletableFuture<Result<JsonObject>> fillInMissingHoldingsRecordId(JsonObject request) {
    final String itemId = request.getString(ITEM_ID);
    if (itemId == null) {
      return ofAsync(() -> request);
    }

    log.info("Attempting to get missing property 'holdingsRecordId' from item {}", itemId);

    return itemRepository.fetchItemAsJson(itemId)
      .thenApply(mapResult(item -> copyProperty(item, request, HOLDINGS_RECORD_ID)));
  }

  private CompletableFuture<Result<JsonObject>> fillInMissingInstanceId(JsonObject request) {
    final String holdingsRecordId = request.getString(HOLDINGS_RECORD_ID);
    if (holdingsRecordId == null) {
      return ofAsync(() -> request);
    }

    log.info("Attempting to get missing property 'instanceId' from holdings record {}", holdingsRecordId);

    return holdingsRepository.fetchAsJson(holdingsRecordId)
      .thenApply(mapResult(holdings -> copyProperty(holdings, request, INSTANCE_ID)));
  }

  private CompletableFuture<Result<Boolean>> shouldFetchInstance(Request request) {
    return ofAsync(() -> errorHandler.hasNone(INVALID_INSTANCE_ID));
  }

  private CompletableFuture<Result<Boolean>> shouldFetchInstanceItems(Request request) {
    return ofAsync(() -> request.isTitleLevel() && errorHandler.hasNone(INVALID_INSTANCE_ID));
  }

  private CompletableFuture<Result<Boolean>> shouldFetchItemAndLoan(RequestAndRelatedRecords records) {
    return ofAsync(() -> errorHandler.hasNone(INVALID_ITEM_ID));
  }

  private CompletableFuture<Result<Request>> fetchInstance(Request request) {
    return succeeded(request)
       .combineAfter(instanceRepository::fetch, Request::withInstance);
      // TODO:  fail if instance doesn't exist
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> fetchItemAndLoan(
    RequestAndRelatedRecords records) {

    Request request = records.getRequest();
    if (request.isTitleLevel() && request.isPage()) {
      return fetchItemAndLoanForPageTlr(records.getRequest())
        .thenApply(mapResult(records::withRequest));
    }

    if (request.isTitleLevel() && request.isRecall()) {
      return fetchItemAndLoanForRecallTlrRequest(records)
        .thenApply(mapResult(records::withRequest));
    }

    return fromFutureResult(findItemForRequest(request))
      .flatMapFuture(this::fetchLoan)
      .flatMapFuture(this::fetchUserForLoan)
      .map(records::withRequest)
      .toCompletableFuture();
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoanForPageTlr(Request request) {
    return request.getOperation() == Request.Operation.CREATE
      ? fetchItemAndLoanForPageTlrCreation(request)
      : fetchItemAndLoanForPageTlrReplacement(request);
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoanForPageTlrCreation(Request request) {
    return fromFutureResult(itemForPageTlrService.findItem(request)
      .thenApply(r -> r.mapFailure(err -> errorHandler.handleValidationError(err,
        NO_AVAILABLE_ITEMS_FOR_TLR, r))))
      .flatMapFuture(this::fetchFirstLoanForUserWithTheSameInstanceId)
      .flatMapFuture(this::fetchUserForLoan)
      .toCompletableFuture();
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoanForPageTlrReplacement(
    Request request) {

    return fromFutureResult(findItemForRequest(request))
      .flatMapFuture(req -> succeeded(req).after(loanRepository::findOpenLoanForRequest)
        .thenApply(r -> r.map(req::withLoan)))
      .flatMapFuture(this::fetchUserForLoan)
      .toCompletableFuture();
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoanForRecallTlrRequest(
    RequestAndRelatedRecords records) {
    Request request = records.getRequest();
    if (errorHandler.hasAny(INVALID_INSTANCE_ID)) {
      return ofAsync(() -> request);
    }

    RequestQueue requestQueue = records.getRequestQueue();
    List<String> recalledLoansIds = requestQueue.getRecalledLoansIds();

    return loanRepository.findLoanWithClosestDueDate(mapToItemIds(request.getInstanceItems()),
        recalledLoansIds)
      //Loan is null means that we have no items that haven't been recalled. In this case we
      //take the loan that has been recalled the least times
      .thenComposeAsync(r -> r.after(when(loan -> shouldLookForTheLeastRecalledLoan(loan,
        recalledLoansIds), ignored -> ofAsync(requestQueue::getTheLeastRecalledLoan),
        result -> ofAsync(() -> result))))
      .thenApply(resultLoan -> resultLoan.map(request::withLoan))
      .thenCompose(r -> r.after(this::findItemForRecall))
      .thenComposeAsync(requestResult -> requestResult.combineAfter(
        this::getUserForExistingLoan, this::addUserToLoan))
      .thenApply(r -> errorHandler.handleValidationResult(r, INSTANCE_DOES_NOT_EXIST, request));
  }

  private CompletableFuture<Result<Boolean>> shouldLookForTheLeastRecalledLoan(Loan loan,
    List<String> recalledLoansIds) {

    return ofAsync(() -> loan == null && !recalledLoansIds.isEmpty());
  }

  private CompletableFuture<Result<Request>> findItemForRecall(Request request) {
    return request.getLoan() == null
      ? completedFuture(findRecallableItemOrFail(request))
      : fetchItemForLoan(request);
  }

  private Result<Request> findRecallableItemOrFail(Request request) {
    return request.getInstanceItems()
      .stream()
      .filter(item -> RECALLABLE_ITEM_STATUSES.contains(item.getStatus()))
      .findFirst()
      .map(request::withItem)
      .map(Result::succeeded)
      .orElseGet(() -> failedValidation("Request has no loan or recallable item", "loan", null))
      .mapFailure(err -> errorHandler.handleValidationError(err,
        TLR_RECALL_WITHOUT_OPEN_LOAN_OR_RECALLABLE_ITEM, request));
  }

  private CompletableFuture<Result<Request>> fetchItemForLoan(Request request) {
    return itemRepository.fetchFor(request.getLoan())
      .thenApply(r -> r.map(request::withItem));
  }

  private CompletableFuture<Result<Request>> findInstanceItems(Request request) {
    return itemByInstanceIdFinder.getItemsByInstanceId(UUID.fromString(request.getInstanceId()))
      .thenApply(r -> r.map(request::withInstanceItems));
  }

  private List<String> mapToItemIds(Collection<Item> items) {
    return items.stream()
      .map(Item::getItemId)
      .collect(toList());
  }

  private CompletableFuture<Result<Request>> fetchLoan(Request request) {
    if (request.isTitleLevel()) {
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

  private Result<Request> validateFulfilmentPreference(Request request) {
    return RequestFulfilmentPreference.allowedValues().stream()
      .filter(value -> value.equals(request.getFulfilmentPreferenceName()))
      .findFirst()
      .map(value -> succeeded(request))
      .orElseGet(() -> failedValidation("fulfilmentPreference must be one of the following: " +
        join(", ", RequestFulfilmentPreference.allowedValues()), "fulfilmentPreference",
        request.getFulfilmentPreferenceName()));
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

  private Result<Request> refuseWhenNoRequestDate(Result<Request> request) {
    return request.next(this::validateIfRequestDateIsPresent)
      .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_ITEM_ID, request));
  }

  private Result<Request> validateIfRequestDateIsPresent(Request context) {
    var requestDate = getDateTimeProperty(context.getRequestRepresentation(), REQUEST_DATE);

    if (requestDate == null) {
      return failedValidation("Cannot create a request with no requestDate", REQUEST_DATE, null);
    } else {
      return of(() -> context);
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

  private CompletableFuture<Result<Request>> findItemForRequest(Request request) {
    return succeeded(request)
      .combineAfter(itemRepository::fetchFor, Request::withItem);
  }

  private CompletableFuture<Result<Request>> fetchUserForLoan(Request request) {
    return succeeded(request)
        .combineAfter(this::getUserForExistingLoan, this::addUserToLoan);
  }

}
