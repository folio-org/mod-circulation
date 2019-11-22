package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.Result.of;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Request;
import org.folio.circulation.support.ClockManager;
import org.joda.time.DateTime;

import org.folio.circulation.domain.ManualBlock;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.http.server.ValidationError;

public class UserManualBlocksValidator {
  private final CollectionResourceClient manualBlocksStorageClient;

  public UserManualBlocksValidator(CollectionResourceClient manualBlocksStorageClient) {
    this.manualBlocksStorageClient = manualBlocksStorageClient;
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> refuseWhenUserIsBlocked(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    final User requester = Optional.ofNullable(requestAndRelatedRecords.getRequest())
      .map(Request::getRequester).orElse(null);

    if (requester != null) {
      final MultipleRecordFetcher<ManualBlock> fetcher = new MultipleRecordFetcher<>(
        manualBlocksStorageClient, "manualblocks", ManualBlock::from);

      return fetcher.findByIndexName(Arrays.asList(requester.getId()), "userId")
        .thenApply(manualBlockResult -> manualBlockResult
          .failWhen(manualBlockMultipleRecords -> of(() ->
                activeManualBlocksExist(manualBlockMultipleRecords)), this::createBlockedValidationError)
          .map(manualBlockMultipleRecords -> requestAndRelatedRecords));
    }
    return CompletableFuture.completedFuture(Result.succeeded(requestAndRelatedRecords));
  }

  private HttpFailure createBlockedValidationError(MultipleRecords<ManualBlock> manualBlocks) {
    final String reason = manualBlocks.getRecords().stream()
      .map(ManualBlock::getDesc).collect(Collectors.joining(";"));

    return singleValidationError(
      new ValidationError("Patron blocked from requesting", "reason", reason));
  }

  private boolean activeManualBlocksExist(MultipleRecords<ManualBlock> manualBlockMultipleRecords) {
    return manualBlockMultipleRecords.getRecords().stream()
        .anyMatch(manualBlock -> isNotActive(manualBlock.getExpirationDate(), manualBlock.getRequests()));
  }

  private boolean isNotActive(DateTime expirationDate, boolean requests) {
    final DateTime now = ClockManager.getClockManager().getDateTime();
    return requests && expirationDate != null && expirationDate.isAfter(now);
  }
}
