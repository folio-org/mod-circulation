package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserManualBlock;
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
      return userManualBlocksFetcher.findByQuery(exactMatch("userId", requester.getId()))
        .thenApply(userManualBlockResult -> userManualBlockResult
          .failWhen(userManualBlockMultipleRecords -> of(() ->
                isUserBlockedManually(userManualBlockMultipleRecords)), this::createUserBlockedValidationError)
          .map(manualBlockMultipleRecords -> requestAndRelatedRecords));
    }
    return CompletableFuture.completedFuture(Result.succeeded(requestAndRelatedRecords));
  }

  private HttpFailure createUserBlockedValidationError(MultipleRecords<UserManualBlock> userManualBlocks) {
    final String reason = userManualBlocks.getRecords().stream()
      .map(UserManualBlock::getDesc).collect(Collectors.joining(";"));

    return singleValidationError(
      new ValidationError("Patron blocked from requesting", "reason", reason));
  }

  private boolean isUserBlockedManually(MultipleRecords<UserManualBlock> userManualBlockMultipleRecords) {
    return userManualBlockMultipleRecords.getRecords().stream()
        .anyMatch(userManualBlock -> isBlockedToCreateRequests(
          userManualBlock.getExpirationDate(), userManualBlock.getRequests()));
  }

  private boolean isBlockedToCreateRequests(DateTime expirationDate, boolean requests) {
    final DateTime now = ClockManager.getClockManager().getDateTime();
    return requests && expirationDate != null && expirationDate.isAfter(now);
  }
}
