package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.storage.ItemByInstanceIdFinder;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.server.ValidationError;

public class RequestLoanValidator {
  private static final PageLimit LOANS_PAGE_LIMIT = limit(10000);
  private final ItemByInstanceIdFinder itemByInstanceIdFinder;
  private final LoanRepository loanRepository;

  public RequestLoanValidator(ItemByInstanceIdFinder itemByInstanceIdFinder, LoanRepository loanRepository) {
    this.itemByInstanceIdFinder = itemByInstanceIdFinder;
    this.loanRepository = loanRepository;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserHasAlreadyBeenLoanedItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    return loanRepository.findOpenLoanForRequest(request).thenApply(loanResult -> loanResult
      .failWhen(loan -> of(() -> loan != null && loan.getUserId().equals(request.getUserId())), loan -> {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("itemId", request.getItemId());
        parameters.put("userId", request.getUserId());
        parameters.put("loanId", loan.getId());

        String message = "This requester currently has this item on loan.";

        return singleValidationError(new ValidationError(message, parameters));
      }).map(loan -> requestAndRelatedRecords));
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserHasAlreadyBeenLoanedOneOfInstancesItems(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final Request request = requestAndRelatedRecords.getRequest();

    return itemByInstanceIdFinder.getItemsByInstanceId(UUID.fromString(request.getInstanceId()))
      .thenCombine(loanRepository.findOpenLoansByUserIdWithItem(LOANS_PAGE_LIMIT, request.getUserId()),
        (items, loans) -> verifyNoMatchBetweenItemsAndLoans(items, loans, requestAndRelatedRecords));
  }

  private Result<RequestAndRelatedRecords> verifyNoMatchBetweenItemsAndLoans(Result<Collection<Item>> items,
      Result<MultipleRecords<Loan>> loans, RequestAndRelatedRecords requestAndRelatedRecords) {
    List<String> itemIds = items.value().stream()
      .map(Item::getItemId)
      .collect(Collectors.toList());

    List<Loan> matchingLoans = loans.value().getRecords().stream()
      .filter(loan -> itemIds.contains(loan.getItemId()))
      .collect(Collectors.toList());

    return matchingLoans.isEmpty()
      ? succeeded(requestAndRelatedRecords)
      : failed(createValidationError(requestAndRelatedRecords, matchingLoans.get(0)));
  }

  private ValidationErrorFailure createValidationError(RequestAndRelatedRecords requestAndRelatedRecords,
      Loan loan) {
    return new ValidationErrorFailure(
      new ValidationError("One of the items of the requested title is already loaned to the requester",
        Map.of(
          "userId", requestAndRelatedRecords.getRequest().getUserId(),
          "itemId", loan.getItemId()
        )));
  }
}
