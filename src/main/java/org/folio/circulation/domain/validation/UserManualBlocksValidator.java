package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Request;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.joda.time.DateTime;

import org.folio.circulation.domain.UserManualBlock;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

public class UserManualBlocksValidator {
  private final FindWithMultipleCqlIndexValues<UserManualBlock> userManualBlocksFetcher;

  public UserManualBlocksValidator(
    FindWithMultipleCqlIndexValues<UserManualBlock> userManualBlocksFetcher) {
    this.userManualBlocksFetcher = userManualBlocksFetcher;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserIsBlocked(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final User requester = Optional.ofNullable(requestAndRelatedRecords.getRequest())
      .map(Request::getRequester).orElse(null);

    if (requester != null) {
      return userManualBlocksFetcher.findByIndexName(Arrays.asList(requester.getId()), "userId")
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
