package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ErrorCode.REQUESTER_ALREADY_HAS_LOAN_FOR_ONE_OF_INSTANCES_ITEMS;
import static org.folio.circulation.support.ErrorCode.REQUESTER_ALREADY_HAS_THIS_ITEM_ON_LOAN;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.collectionAsString;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class RequestLoanValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final PageLimit LOANS_PAGE_LIMIT = limit(10000);
  private final ItemByInstanceIdFinder itemByInstanceIdFinder;
  private final LoanRepository loanRepository;

  public RequestLoanValidator(ItemByInstanceIdFinder itemByInstanceIdFinder, LoanRepository loanRepository) {
    this.itemByInstanceIdFinder = itemByInstanceIdFinder;
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserHasAlreadyBeenLoanedItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenUserHasAlreadyBeenLoanedItem:: parameters requestAndRelatedRecords: {}",
      requestAndRelatedRecords);

    final Request request = requestAndRelatedRecords.getRequest();

    return loanRepository.findOpenLoanForRequest(request).thenApply(loanResult -> loanResult
      .failWhen(loan -> of(() -> loan != null && loan.getUserId().equals(request.getUserId())), loan -> {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("itemId", request.getItemId());
        parameters.put("userId", request.getUserId());
        parameters.put("loanId", loan.getId());

        String message = "This requester already has this item on loan";

        return singleValidationError(new ValidationError(message, parameters,
          REQUESTER_ALREADY_HAS_THIS_ITEM_ON_LOAN));
      }).map(loan -> requestAndRelatedRecords));
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>>
  refuseWhenUserHasAlreadyBeenLoanedOneOfInstancesItems(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenUserHasAlreadyBeenLoanedOneOfInstancesItems:: parameters " +
        "requestAndRelatedRecords: {}", requestAndRelatedRecords);

    final Request request = requestAndRelatedRecords.getRequest();
    final UUID instanceId = UUID.fromString(request.getInstanceId());

    return itemByInstanceIdFinder.getItemsByInstanceId(instanceId, false)
      .thenCombine(
        loanRepository.findOpenLoansByUserIdWithItem(LOANS_PAGE_LIMIT, request.getUserId()),
        (items, loans) -> verifyNoMatchOrFailAsAlreadyLoaned(items, loans, requestAndRelatedRecords)
      );
  }

  private Result<RequestAndRelatedRecords> verifyNoMatchOrFailAsAlreadyLoaned(
    Result<Collection<Item>> items, Result<MultipleRecords<Loan>> loans,
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("verifyNoMatchOrFailAsAlreadyLoaned:: parameters " +
      "items: {}, loans: {}, requestAndRelatedRecords: {}",
      () -> collectionAsString(items.value()), () -> multipleRecordsAsString(loans.value()),
      () -> requestAndRelatedRecords);

    List<String> itemIds = items.value().stream()
      .map(Item::getItemId)
      .collect(Collectors.toList());

    List<Loan> matchingLoans = loans.value().getRecords().stream()
      .filter(loan -> itemIds.contains(loan.getItemId()))
      .collect(Collectors.toList());

    return matchingLoans.isEmpty()
      ? succeeded(requestAndRelatedRecords)
      : oneOfTheItemsIsAlreadyLoanedFailure(requestAndRelatedRecords, matchingLoans.get(0));
  }

  private Result<RequestAndRelatedRecords> oneOfTheItemsIsAlreadyLoanedFailure(
    RequestAndRelatedRecords requestAndRelatedRecords, Loan loan) {

    log.debug("oneOfTheItemsIsAlreadyLoanedFailure:: parameters " +
      "requestAndRelatedRecords: {}, loan: {}", requestAndRelatedRecords, loan);

    HashMap<String, String> parameters = new HashMap<>();
    parameters.put("userId", requestAndRelatedRecords.getRequest().getUserId());
    parameters.put("itemId", loan.getItemId());

    return failedValidation("This requester already has a loan for one of the instance's items",
      parameters, REQUESTER_ALREADY_HAS_LOAN_FOR_ONE_OF_INSTANCES_ITEMS);
  }
}
