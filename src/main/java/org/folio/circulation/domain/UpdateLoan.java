package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;

import static org.folio.circulation.resources.LoanCollectionResourceHelper.createItemNotFoundValidator;
import static org.folio.circulation.resources.LoanCollectionResourceHelper.getServicePointsForLoanAndRelated;
import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang.StringUtils;
import org.folio.circulation.domain.notice.schedule.DueDateScheduledNoticeService;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointLoanLocationValidator;
import org.folio.circulation.resources.LoanCollectionResourceHelper;
import org.folio.circulation.resources.LoanNoticeSender;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.JsonResponseResult;
import org.folio.circulation.support.NoContentResult;
import org.folio.circulation.support.ResponseWritableResult;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class UpdateLoan {

  private Clients clients;
  private ClosedLibraryStrategyService closedLibraryStrategyService;
  private final LoanRepository loanRepository;
  private LoanPolicyRepository loanPolicyRepository;
  private final DueDateScheduledNoticeService scheduledNoticeService;

  public UpdateLoan(Clients clients,
      LoanRepository loanRepository) {
    this.clients = clients;
    this.loanRepository = loanRepository;
    scheduledNoticeService = DueDateScheduledNoticeService.using(clients);


  }

  public UpdateLoan(Clients clients,
      LoanRepository loanRepository,
      LoanPolicyRepository loanPolicyRepository) {
    closedLibraryStrategyService = ClosedLibraryStrategyService.using(clients,
        DateTime.now(DateTimeZone.UTC), false);
    this.loanPolicyRepository = loanPolicyRepository;
    this.loanRepository = loanRepository;
    this.scheduledNoticeService = DueDateScheduledNoticeService.using(clients);
  }

  /**
   * Updates the loan due date for the loan associated with this newly created
   * recall request. No modifications are made if the request is not a recall.
   * Depending on loan/request policies, the loan date may not be updated.
   *
   * @param requestAndRelatedRecords request and related records.
   * @return the request and related records with the possibly updated loan.
   */
  CompletableFuture<Result<RequestAndRelatedRecords>> onRequestCreateOrUpdate(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    Request request = requestAndRelatedRecords.getRequest();
    Loan loan = request.getLoan();

    if (request.getRequestType() == RequestType.RECALL && loan != null) {
      return loanRepository.getById(loan.getId())
          .thenComposeAsync(r -> r.after(l -> recall(l, requestAndRelatedRecords, request)));
    } else {
      return completedFuture(succeeded(requestAndRelatedRecords));
    }
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> replaceLoan(Loan loan) {
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final ServicePointRepository servicePointRepository = new ServicePointRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true, true);
    final UserRepository userRepository = new UserRepository(clients);

    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);
    final UpdateItem updateItem = new UpdateItem(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> singleValidationError("proxyUserId is not valid", "proxyUserId",
      loan.getProxyUserId()));

    final ItemNotFoundValidator itemNotFoundValidator = createItemNotFoundValidator(loan);

    final ServicePointLoanLocationValidator spLoanLocationValidator =
      new ServicePointLoanLocationValidator();


    final LoanNoticeSender loanNoticeSender = LoanNoticeSender.using(clients);

    return completedFuture(succeeded(new LoanAndRelatedRecords(loan)))
      .thenCompose(larrResult ->
        getServicePointsForLoanAndRelated(larrResult, servicePointRepository))
      .thenApply(LoanCollectionResourceHelper::refuseWhenNotOpenOrClosed)
      .thenApply(LoanCollectionResourceHelper::refuseWhenOpenAndNoUserId)
      .thenApply(spLoanLocationValidator::checkServicePointLoanLocation)
      .thenApply(LoanCollectionResourceHelper::refuseWhenClosedAndNoCheckInServicePointId)
      .thenCombineAsync(itemRepository.fetchFor(loan), LoanCollectionResourceHelper::addItem)
      .thenCombineAsync(userRepository.getUser(loan.getUserId()), LoanCollectionResourceHelper::addUser)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenCombineAsync(requestQueueRepository.get(loan.getItemId()), LoanCollectionResourceHelper::addRequestQueue)
      .thenComposeAsync(result -> result.after(requestQueueUpdate::onCheckIn))
      .thenComposeAsync(result -> result.after(updateItem::onLoanUpdate))
      // Loan must be updated after item
      // due to snapshot of item status stored with the loan
      // as this is how the loan action history is populated
      .thenComposeAsync(result -> result.after(loanRepository::updateLoan))
      .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
      .thenCompose(r -> r.after(loanNoticeSender::sendManualDueDateChangeNotice));
  }

  private Result<LoanAndRelatedRecords> updateLoanAction(LoanAndRelatedRecords loanAndRelatedRecords, Request request) {
    Loan loan = loanAndRelatedRecords.getLoan();
    LoanAction action = request.actionOnCreateOrUpdate();

    if (action != null) {
      loan.changeAction(action);
      loan.changeItemStatus(request.getItem().getStatus().getValue());
    }

    return of(() -> loanAndRelatedRecords);
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> recall(Loan loan,
      RequestAndRelatedRecords requestAndRelatedRecords, Request request) {
    if (loan.wasDueDateChangedByRecall()) {
      // We don't need to apply the recall
      return completedFuture(succeeded(requestAndRelatedRecords));
    } else {
      return Result.of(() -> new LoanAndRelatedRecords(loan,
          requestAndRelatedRecords.getTimeZone()))
          .after(loanPolicyRepository::lookupLoanPolicy)
          .thenApply(r -> r.next(this::recall))
          .thenApply(r -> r.next(recallResult -> updateLoanAction(recallResult, request)))
          .thenComposeAsync(r -> r.after(closedLibraryStrategyService::applyClosedLibraryDueDateManagement))
          .thenComposeAsync(r -> r.after(loanRepository::updateLoan))
          .thenApply(r -> r.next(scheduledNoticeService::rescheduleDueDateNotices))
          .thenApply(r -> r.map(v -> requestAndRelatedRecords.withRequest(request.withLoan(v.getLoan()))));
    }
  }

  //TODO: Possibly combine this with LoanRenewalService?
  private Result<LoanAndRelatedRecords> recall(LoanAndRelatedRecords loanAndRelatedRecords) {
    final Loan loan = loanAndRelatedRecords.getLoan();
    LoanPolicy loanPolicy = loan.getLoanPolicy();

    return loanPolicy.recall(loan)
        .map(loanAndRelatedRecords::withLoan);
  }
}
