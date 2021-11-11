package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.domain.representations.RequestProperties.INSTANCE_ID;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_INSTANCE_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_ITEM_ID;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PICKUP_SERVICE_POINT;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.INVALID_PROXY_RELATIONSHIP;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.MappingFunctions.when;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestLevel;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
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
import org.folio.circulation.support.BadRequestFailure;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

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

  RequestFromRepresentationService(InstanceRepository instanceRepository,
    ItemRepository itemRepository, RequestQueueRepository requestQueueRepository,
    UserRepository userRepository, LoanRepository loanRepository,
    ServicePointRepository servicePointRepository, ConfigurationRepository configurationRepository,
    ProxyRelationshipValidator proxyRelationshipValidator,
    ServicePointPickupLocationValidator pickupLocationValidator,
    CirculationErrorHandler errorHandler) {

    this.instanceRepository = instanceRepository;
    this.loanRepository = loanRepository;
    this.itemRepository = itemRepository;
    this.requestQueueRepository = requestQueueRepository;
    this.userRepository = userRepository;
    this.servicePointRepository = servicePointRepository;
    this.configurationRepository = configurationRepository;
    this.proxyRelationshipValidator = proxyRelationshipValidator;
    this.pickupLocationValidator = pickupLocationValidator;
    this.errorHandler = errorHandler;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequestFrom(JsonObject representation) {
    return initRepresentationValidationContext(representation)
      .thenApply(r -> r.next(this::validateStatus))
      .thenApply(r -> r.next(this::validateRequestLevel))
      .thenApply(r -> r.next(this::refuseWhenNoInstanceId)
        .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_INSTANCE_ID, r)))
      .thenApply(r -> r.next(this::refuseWhenNoItemId)
        .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_ITEM_ID, r)))
      .thenApply(r -> r.next(this::refuseWhenNoInstanceId)
        .mapFailure(err -> errorHandler.handleValidationError(err, INVALID_INSTANCE_ID, r)))
      .thenApply(r -> r.map(this::removeRelatedRecordInformation))
      .thenApply(r -> r.map(this::removeProcessingParameters))
      .thenApply(r -> r.map(ctx -> Request.from(ctx.tlrSettingsConfiguration, ctx.representation)))
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

  private CompletableFuture<Result<RepresentationValidationContext>> initRepresentationValidationContext(
    JsonObject representation) {

    return completedFuture(succeeded(new RepresentationValidationContext().withRepresentation(representation)))
      .thenCompose(r -> r.combineAfter(configurationRepository::lookupTlrSettings,
        RepresentationValidationContext::withTlrSettingsConfiguration));
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
  }

  private CompletableFuture<Result<Request>> fetchItemAndLoan(Request request) {
    return succeeded(request)
      .combineAfter(itemRepository::fetchFor, Request::withItem)
//      .thenComposeAsync(r -> r.combineAfter(loanRepository::findOpenLoanForRequest, Request::withLoan))
      .thenComposeAsync(r -> r.after(this::fetchLoan))
      .thenComposeAsync(r -> r.combineAfter(this::getUserForExistingLoan, this::addUserToLoan));
  }

  private CompletableFuture<Result<Request>> fetchLoan(Request request) {
    if (request.getTlrSettingsConfiguration().isTitleLevelRequestsFeatureEnabled()) {
      // There can be multiple loans for items of the same title, but we're only saving one of
      // them because it's only used to determine whether the patron has open loans for any
      // of the title's items

      return loanRepository.findOpenLoansByUserIdWithItem(LOANS_PAGE_LIMIT, request.getUserId())
        .thenApply(r -> r.map(loans -> getLoanForItemOfTheSameInstance(request, loans)))
        .thenApply(r -> r.map(request::withLoan));
    }
    else {
      return loanRepository.findOpenLoanForRequest(request)
        .thenApply(r -> r.map(request::withLoan));
    }
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

  private Result<RepresentationValidationContext> validateStatus(
    RepresentationValidationContext context) {

    JsonObject representation = context.getRepresentation();
    RequestStatus status = RequestStatus.from(representation);

    if (!status.isValid()) {
      return failed(new BadRequestFailure(RequestStatus.invalidStatusErrorMessage()));
    }
    else {
      status.writeTo(representation);
      return succeeded(context);
    }
  }

  private Result<RepresentationValidationContext> validateRequestLevel(RepresentationValidationContext context) {
    JsonObject representation = context.getRepresentation();

    RequestLevel requestLevel = RequestLevel.from(representation.getString("requestLevel"));
    boolean tlrEnabled = context.getTlrSettingsConfiguration().isTitleLevelRequestsFeatureEnabled();

    List<RequestLevel> allowedStatuses = tlrEnabled
      ? List.of(RequestLevel.ITEM, RequestLevel.TITLE)
      : List.of(RequestLevel.ITEM);

    if (!allowedStatuses.contains(requestLevel)) {
      return failed(new BadRequestFailure("requestLevel must be one of the following: " +
        allowedStatuses.stream()
          .map(existingLevel -> StringUtils.wrap(existingLevel.getValue(), '"'))
          .collect(Collectors.joining(", "))));
    }

    return succeeded(context);
  }

  private Result<RepresentationValidationContext> refuseWhenNoInstanceId(
    RepresentationValidationContext context) {

    JsonObject representation = context.getRepresentation();
    String instanceId = getProperty(representation, INSTANCE_ID);

    if (isBlank(instanceId)) {
      return failedValidation("Cannot create a request with no instance ID", "instanceId",
        instanceId);
    }
    else {
      return of(() -> context);
    }
  }

    private Result<RepresentationValidationContext> refuseWhenNoItemId(
      RepresentationValidationContext context) {

    JsonObject representation = context.getRepresentation();
    String itemId = getProperty(representation, ITEM_ID);
    boolean tlrEnabled = context.getTlrSettingsConfiguration().isTitleLevelRequestsFeatureEnabled();

    if (!tlrEnabled && isBlank(itemId)) {
      return failedValidation("Cannot create a request with no item ID", "itemId", itemId);
    }
    else {
      return of(() -> context);
    }
  }

  private Result<RepresentationValidationContext> refuseWhenNoInstanceId(RepresentationValidationContext context) {
    JsonObject representation = context.getRepresentation();
    String instanceId = getProperty(representation, INSTANCE_ID);

    if (isBlank(instanceId)) {
      return failedValidation("Cannot create a request with no instance ID", "instanceId", instanceId);
    }
    else {
      return of(() -> context);
    }
  }

  private RepresentationValidationContext removeRelatedRecordInformation(RepresentationValidationContext context) {
    JsonObject representation = context.getRepresentation();

    representation.remove("item");
    representation.remove("requester");
    representation.remove("proxy");
    representation.remove("loan");
    representation.remove("pickupServicePoint");
    representation.remove("deliveryAddress");

    return context;
  }

  private RepresentationValidationContext removeProcessingParameters(RepresentationValidationContext context) {
    JsonObject representation = context.getRepresentation();

    representation.remove("requestProcessingParameters");

    return context;
  }

  @AllArgsConstructor
  @NoArgsConstructor(force = true)
  @With
  @Getter
  private static final class RepresentationValidationContext {
    private final JsonObject representation;
    private final TlrSettingsConfiguration tlrSettingsConfiguration;
  }
}
