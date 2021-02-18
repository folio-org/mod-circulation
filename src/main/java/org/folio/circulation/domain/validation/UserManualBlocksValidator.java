package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserManualBlock;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;

public class UserManualBlocksValidator {
  private final FindWithCqlQuery<UserManualBlock> userManualBlocksFetcher;

  public UserManualBlocksValidator(FindWithCqlQuery<UserManualBlock> userManualBlocksFetcher) {
    this.userManualBlocksFetcher = userManualBlocksFetcher;
  }

  public UserManualBlocksValidator(Clients clients) {
    this(findWithCqlQuery(clients.userManualBlocksStorageClient(), "manualblocks",
      UserManualBlock::from));
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserIsBlocked(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final User requester = Optional.ofNullable(requestAndRelatedRecords.getRequest())
      .map(Request::getRequester).orElse(null);

    if (requester != null) {
      return failIfPatronIsBlocked(requester.getId(), "Patron blocked from requesting")
        .thenApply(r -> r.map(records -> requestAndRelatedRecords));
    }
    return CompletableFuture.completedFuture(Result.succeeded(requestAndRelatedRecords));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenUserIsBlocked(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return failIfPatronIsBlocked(loanAndRelatedRecords.getUserId(), "Patron blocked from borrowing")
      .thenApply(r -> r.map(records -> loanAndRelatedRecords));
  }

  public CompletableFuture<Result<RenewalContext>> refuseWhenUserIsBlocked(
    RenewalContext renewalContext) {

    return failIfPatronIsBlocked(renewalContext.getLoan().getUserId(), "Patron blocked from renewing")
      .thenApply(r -> r.map(records -> renewalContext));
  }

  private CompletableFuture<Result<MultipleRecords<UserManualBlock>>> failIfPatronIsBlocked(
    String userId, String message) {

    return userManualBlocksFetcher.findByQuery(exactMatch("userId", userId))
      .thenApply(userManualBlockResult -> userManualBlockResult
        .failWhen(userManualBlockMultipleRecords -> of(() ->
            isUserBlockedManually(userManualBlockMultipleRecords)),
          userManualBlocks -> createUserBlockedValidationError(userManualBlocks, message)));
  }

  private HttpFailure createUserBlockedValidationError(
    MultipleRecords<UserManualBlock> userManualBlocks, String message) {

    final String reason = userManualBlocks.getRecords().stream()
      .map(UserManualBlock::getDesc).collect(Collectors.joining(";"));

    return singleValidationError(new ValidationError(message, "reason", reason));
  }

  private boolean isUserBlockedManually(MultipleRecords<UserManualBlock> userManualBlockMultipleRecords) {
    return userManualBlockMultipleRecords.getRecords().stream()
        .anyMatch(userManualBlock -> isBlockedToCreateRequests(
          userManualBlock.getExpirationDate(), userManualBlock.getRequests()));
  }

  private boolean isBlockedToCreateRequests(DateTime expirationDate, boolean blockRequests) {
    final DateTime now = ClockManager.getClockManager().getDateTime();
    return blockRequests && (expirationDate == null || expirationDate.isAfter(now));
  }
}
