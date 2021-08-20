package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.UserManualBlock;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;
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

    return Optional.ofNullable(requestAndRelatedRecords.getRequest())
      .map(Request::getRequester)
      .map(user -> failIfPatronIsBlocked(userManualBlock -> isBlockedAction(
        userManualBlock.getExpirationDate(), userManualBlock.getRequests()), user.getId(),
        "Patron blocked from requesting")
        .thenApply(r -> r.map(records -> requestAndRelatedRecords)))
      .orElseGet(() -> ofAsync(() -> requestAndRelatedRecords));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> refuseWhenUserIsBlocked(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return failIfPatronIsBlocked(userManualBlock -> isBlockedAction(
        userManualBlock.getExpirationDate(), userManualBlock.getBorrowing()),
        loanAndRelatedRecords.getUserId(), "Patron blocked from borrowing")
      .thenApply(r -> r.map(records -> loanAndRelatedRecords));
  }

  public CompletableFuture<Result<RenewalContext>> refuseWhenUserIsBlocked(
    RenewalContext renewalContext) {

    return failIfPatronIsBlocked(userManualBlock -> isBlockedAction(
        userManualBlock.getExpirationDate(), userManualBlock.getRenewals()),
        renewalContext.getLoan().getUserId(), "Patron blocked from renewing")
      .thenApply(r -> r.map(records -> renewalContext));
  }

  private CompletableFuture<Result<MultipleRecords<UserManualBlock>>> failIfPatronIsBlocked(
    Predicate<UserManualBlock> isUserBlocked, String userId, String message) {

    return userManualBlocksFetcher.findByQuery(exactMatch("userId", userId))
      .thenApply(userManualBlockResult -> userManualBlockResult
        .failWhen(userManualBlockMultipleRecords -> of(() ->
            isUserBlockedManually(userManualBlockMultipleRecords, isUserBlocked)),
          userManualBlocks -> createUserBlockedValidationError(userManualBlocks, message)));
  }

  private HttpFailure createUserBlockedValidationError(
    MultipleRecords<UserManualBlock> userManualBlocks, String message) {

    final String reason = userManualBlocks.getRecords().stream()
      .map(UserManualBlock::getDesc).collect(Collectors.joining(";"));

    return singleValidationError(new ValidationError(message, "reason", reason));
  }

  private boolean isUserBlockedManually(MultipleRecords<UserManualBlock> userManualBlockMultipleRecords,
    Predicate<UserManualBlock> isUserBlocked) {

    return userManualBlockMultipleRecords.getRecords().stream()
        .anyMatch(isUserBlocked);
  }

  private boolean isBlockedAction(DateTime expirationDate, boolean isBlocked) {
    final DateTime now = ClockUtil.getDateTime();
    return isBlocked && (expirationDate == null || expirationDate.isAfter(now));
  }
}
