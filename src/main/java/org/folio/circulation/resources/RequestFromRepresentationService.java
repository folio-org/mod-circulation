package org.folio.circulation.resources;

import static java.lang.String.join;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.circulation.domain.EcsRequestPhase.INTERMEDIATE;
import static org.folio.circulation.domain.EcsRequestPhase.PRIMARY;
import static org.folio.circulation.domain.RequestLevel.ITEM;
import static org.folio.circulation.domain.RequestLevel.TITLE;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.domain.RequestTypeItemStatusWhiteList.canCreateRequestForItem;
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
import static org.folio.circulation.support.ErrorCode.FULFILLMENT_PREFERENCE_IS_NOT_ALLOWED;
import static org.folio.circulation.support.ErrorCode.CANNOT_CREATE_PAGE_TLR_WITHOUT_ITEM_ID;
import static org.folio.circulation.support.ErrorCode.REQUEST_LEVEL_IS_NOT_ALLOWED;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.EcsRequestPhase;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestFulfillmentPreference;
import org.folio.circulation.domain.RequestLevel;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointPickupLocationValidator;
import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.HoldingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.InstanceRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestPolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.services.ItemForTlrService;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.request.RequestRelatedRepositories;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

class RequestFromRepresentationService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final PageLimit LOANS_PAGE_LIMIT = limit(10000);
  private final Request.Operation operation;
  private final InstanceRepository instanceRepository;
  private final ItemRepository itemRepository;
  private final HoldingsRepository holdingsRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  private final ServicePointRepository servicePointRepository;
  private final ConfigurationRepository configurationRepository;
  private final SettingsRepository settingsRepository;
  private final RequestPolicyRepository requestPolicyRepository;
  private final ProxyRelationshipValidator proxyRelationshipValidator;
  private final ServicePointPickupLocationValidator pickupLocationValidator;
  private final CirculationErrorHandler errorHandler;
  private final ItemByInstanceIdFinder itemByInstanceIdFinder;
  private final ItemForTlrService itemForTlrService;

  public RequestFromRepresentationService(Request.Operation operation,
    RequestRelatedRepositories repositories, ProxyRelationshipValidator proxyRelationshipValidator,
    ServicePointPickupLocationValidator pickupLocationValidator,
    CirculationErrorHandler errorHandler, ItemByInstanceIdFinder itemByInstanceIdFinder,
    ItemForTlrService itemForTlrService) {

    this.operation = operation;

    this.instanceRepository = repositories.getInstanceRepository();
    this.itemRepository = repositories.getItemRepository();
    this.holdingsRepository = repositories.getHoldingsRepository();
    this.requestQueueRepository = repositories.getRequestQueueRepository();
    this.userRepository = repositories.getUserRepository();
    this.loanRepository = repositories.getLoanRepository();
    this.servicePointRepository = repositories.getServicePointRepository();
    this.configurationRepository = repositories.getConfigurationRepository();
    this.settingsRepository = repositories.getSettingsRepository();
    this.requestPolicyRepository = repositories.getRequestPolicyRepository();

    this.proxyRelationshipValidator = proxyRelationshipValidator;
    this.pickupLocationValidator = pickupLocationValidator;
    this.errorHandler = errorHandler;
    this.itemByInstanceIdFinder = itemByInstanceIdFinder;
    this.itemForTlrService = itemForTlrService;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequestFrom(JsonObject representation) {

    return settingsRepository.lookupTlrSettings()
      .thenCompose(r -> r.after(tlrSettings -> initRequest(operation, tlrSettings, representation)))
      .thenApply(r -> r.next(this::validateStatus))
      .thenApply(r -> r.next(this::validateRequestLevel))
      .thenApply(r -> r.next(this::validatefulfillmentPreference))
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
      .thenComposeAsync(r -> r.combineAfter(userRepository::getUser, Request::withRequester))
      .thenComposeAsync(r -> r.combineAfter(userRepository::getProxyUser, Request::withProxy))
      .thenComposeAsync(r -> r.after(when(
        this::shouldFetchInstanceItems, this::findInstanceItemsAndPolicies, req -> ofAsync(() -> req))))
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
      log.warn("fillInMissingInstanceId:: holdingRecordId is null");
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
    return ofAsync(() -> errorHandler.hasNone(INVALID_ITEM_ID, INSTANCE_DOES_NOT_EXIST));
  }

  private CompletableFuture<Result<Request>> fetchInstance(Request request) {
    return succeeded(request)
      .combineAfter(instanceRepository::fetch, Request::withInstance)
      .thenApply(r -> {
        Request updatedRequest = r.value();
        if (updatedRequest.getInstance().isNotFound()) {
          errorHandler.handleValidationResult(failedValidation("Instance does not exist",
              "instanceId", updatedRequest.getInstanceId()), INSTANCE_DOES_NOT_EXIST,
            updatedRequest);
        }
        return succeeded(updatedRequest);
      });
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> fetchItemAndLoan(
    RequestAndRelatedRecords records) {

    log.debug("fetchItemAndLoan:: parameters records: {}", () -> records);
    Request request = records.getRequest();
    Function<RequestAndRelatedRecords, CompletableFuture<Result<Request>>>
      itemAndLoanFetchingFunction;
    EcsRequestPhase ecsRequestPhase = request.getEcsRequestPhase();
    log.info("fetchItemAndLoan:: ECS request phase is {}", ecsRequestPhase);
    if (ecsRequestPhase == PRIMARY || ecsRequestPhase == INTERMEDIATE) {
      log.info("fetchItemAndLoan:: ECS request phase {} detected, using default item fetcher",
        ecsRequestPhase);
      itemAndLoanFetchingFunction = this::fetchItemAndLoanDefault;
    }
    else if (request.isTitleLevel() && request.isPage()) {
      itemAndLoanFetchingFunction = this::fetchItemAndLoanForPageTlr;
    }
    else if (request.isTitleLevel() && request.isRecall()) {
      if (request.getOperation() == Request.Operation.REPLACE) {
        itemAndLoanFetchingFunction = this::fetchItemAndLoanDefault;
      } else {
        itemAndLoanFetchingFunction = this::fetchItemAndLoanForRecallTlrCreation;
      }
    }
    else {
      itemAndLoanFetchingFunction = this::fetchItemAndLoanDefault;
    }

    return completedFuture(records)
      .thenCompose(itemAndLoanFetchingFunction)
      .thenApply(mapResult(records::withRequest));
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoanDefault(
    RequestAndRelatedRecords records) {

    log.debug("fetchItemAndLoanDefault:: parameters records: {}", () -> records);

    return fromFutureResult(findItemForRequest(records.getRequest()))
      .flatMapFuture(this::fetchLoan)
      .flatMapFuture(this::fetchUserForLoan)
      .toCompletableFuture();
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoanForPageTlr(
    RequestAndRelatedRecords records) {

    Request request = records.getRequest();
    return request.getOperation() == Request.Operation.CREATE
      ? fetchItemAndLoanForPageTlrCreation(request)
      : fetchItemAndLoanForPageTlrReplacement(request);
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoanForPageTlrCreation(Request request) {
    return fromFutureResult(itemForTlrService.findClosestAvailablePageableItem(request)
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

  private CompletableFuture<Result<Request>> fetchItemAndLoanForRecallTlrCreation(
    RequestAndRelatedRecords records) {

    Request request = records.getRequest();
    if (errorHandler.hasAny(INVALID_INSTANCE_ID)) {
      log.warn("fetchItemAndLoanForRecallTlrCreation:: invalid instanceId");
      return ofAsync(() -> request);
    }

    RequestQueue requestQueue = records.getRequestQueue();
    List<String> recalledLoansIds = requestQueue.getRecalledLoansIds();

    List<String> recallableItemIds = request.getInstanceItems()
      .stream()
      .filter(item -> canCreateRequestForItem(item.getStatus(), RECALL))
      .map(Item::getItemId)
      .filter(itemId -> request.getInstanceItemsRequestPolicies().get(itemId)
        .allowsType(RECALL))
      .collect(toList());

    return loanRepository.findLoanWithClosestDueDate(recallableItemIds, recalledLoansIds)
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
    log.debug("findItemForRecall:: parameters request: {}", () -> request);
    Loan loan = request.getLoan();
    if (loan != null) {
      return itemRepository.fetchFor(loan)
        .thenApply(r -> r.map(request::withItem));
    }

    return completedFuture(findRecallableItemOrFail(request));
  }

  private Result<Request> findRecallableItemOrFail(Request request) {
    return request.getInstanceItems()
      .stream()
      .filter(item -> canCreateRequestForItem(item.getStatus(), RECALL))
      .findFirst()
      .map(request::withItem)
      .map(Result::succeeded)
      .orElseGet(() -> failedValidation("Request has no loan or recallable item", "loan", null))
      .mapFailure(err -> errorHandler.handleValidationError(err,
        TLR_RECALL_WITHOUT_OPEN_LOAN_OR_RECALLABLE_ITEM, request));
  }

  private CompletableFuture<Result<Request>> findInstanceItemsAndPolicies(Request request) {
    log.debug("findInstanceItemsAndPolicies:: parameters request: {}", () -> request);
    final var instanceId = UUID.fromString(request.getInstanceId());
    return itemByInstanceIdFinder.getItemsByInstanceId(instanceId, false)
      .thenApply(r -> r.map(request::withInstanceItems))
      .thenCompose(r -> r.after(requestPolicyRepository::lookupRequestPolicies));
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
        "Request level must be one of the following: " + allowedStatusesJoined, "requestLevel",
        requestLevelRaw, REQUEST_LEVEL_IS_NOT_ALLOWED);
    }

    return succeeded(request);
  }

  private Result<Request> validatefulfillmentPreference(Request request) {
    return RequestFulfillmentPreference.allowedValues().stream()
      .filter(value -> value.equals(request.getfulfillmentPreferenceName()))
      .findFirst()
      .map(value -> succeeded(request))
      .orElseGet(() -> failedValidation("Fulfillment preference must be one of the following: " +
        join(", ", RequestFulfillmentPreference.allowedValues()), "fulfillmentPreference",
        request.getfulfillmentPreferenceName(), FULFILLMENT_PREFERENCE_IS_NOT_ALLOWED));
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
    EcsRequestPhase ecsRequestPhase = request.getEcsRequestPhase();
    if (ecsRequestPhase == PRIMARY || ecsRequestPhase == INTERMEDIATE) {
      log.info("validateAbsenceOfItemLinkInTlr:: ECS request phase {} detected, skipping", ecsRequestPhase);
      return of(() -> request);
    }

    String itemId = request.getItemId();
    String holdingsRecordId = request.getHoldingsRecordId();

    if (errorHandler.hasNone(INVALID_ITEM_ID, INVALID_HOLDINGS_RECORD_ID)
      && request.getOperation() == Request.Operation.CREATE
      && request.getRequestLevel() == TITLE
      && (isNotBlank(itemId) || isNotBlank(holdingsRecordId))) {

      Map<String, String> errorParameters = new HashMap<>();
      errorParameters.put("itemId", itemId);
      errorParameters.put("holdingsRecordId", holdingsRecordId);
      return failedValidation("Cannot create a title level page request " +
          "for this instance ID with no item ID", errorParameters, CANNOT_CREATE_PAGE_TLR_WITHOUT_ITEM_ID);
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

    JsonObject printDetails = representation.getJsonObject("printDetails");
    if (printDetails != null && printDetails.containsKey("lastPrintRequester")) {
      printDetails.remove("lastPrintRequester");
    }
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
