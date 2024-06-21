package org.folio.circulation.domain.validation;

import static org.folio.circulation.support.ErrorCode.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.support.ValidationErrorFailure.singleValidationError;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.results.Result.of;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

public class UserManualBlocksValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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

    log.debug("refuseWhenUserIsBlocked:: parameters requestAndRelatedRecords: {}",
      requestAndRelatedRecords);

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

    log.debug("refuseWhenUserIsBlocked:: parameters loanAndRelatedRecords: {}",
      loanAndRelatedRecords);

    return failIfPatronIsBlocked(userManualBlock -> isBlockedAction(
        userManualBlock.getExpirationDate(), userManualBlock.getBorrowing()),
        loanAndRelatedRecords.getUserId(), "Patron blocked from borrowing")
      .thenApply(r -> r.map(records -> loanAndRelatedRecords));
  }

  public CompletableFuture<Result<RenewalContext>> refuseWhenUserIsBlocked(
    RenewalContext renewalContext) {

    log.debug("refuseWhenUserIsBlocked:: parameters renewalContext: {}", renewalContext);

    return failIfPatronIsBlocked(userManualBlock -> isBlockedAction(
        userManualBlock.getExpirationDate(), userManualBlock.getRenewals()),
        renewalContext.getLoan().getUserId(), "Patron blocked from renewing")
      .thenApply(r -> r.map(records -> renewalContext));
  }

  private CompletableFuture<Result<MultipleRecords<UserManualBlock>>> failIfPatronIsBlocked(
    Predicate<UserManualBlock> isUserBlocked, String userId, String message) {

    log.debug("failIfPatronIsBlocked:: parameters isUserBlocked, userId: {}, message: {}", userId,
      message);

    return userManualBlocksFetcher.findByQuery(exactMatch("userId", userId))
      .thenApply(userManualBlockResult -> userManualBlockResult
        .failWhen(userManualBlockMultipleRecords -> of(() ->
            isUserBlockedManually(userManualBlockMultipleRecords, isUserBlocked)),
          userManualBlocks -> createUserBlockedValidationError(userManualBlocks, message)));
  }

  private HttpFailure createUserBlockedValidationError(
    MultipleRecords<UserManualBlock> userManualBlocks, String message) {

    log.debug("createUserBlockedValidationError:: parameters isUserBlocked, userId: {}, message: {}",
      () -> multipleRecordsAsString(userManualBlocks), () -> message);

    final String reason = userManualBlocks.getRecords().stream()
      .map(UserManualBlock::getDesc).collect(Collectors.joining(";"));

    return singleValidationError(new ValidationError(message, "reason", reason, USER_IS_BLOCKED_MANUALLY));
  }

  private boolean isUserBlockedManually(MultipleRecords<UserManualBlock> userManualBlockMultipleRecords,
    Predicate<UserManualBlock> isUserBlocked) {

    log.debug("isUserBlockedManually:: parameters isUserBlocked, " +
        "userManualBlockMultipleRecords: {}, isUserBlocked",
      () -> multipleRecordsAsString(userManualBlockMultipleRecords));

    return userManualBlockMultipleRecords.getRecords().stream()
        .anyMatch(isUserBlocked);
  }

  private boolean isBlockedAction(ZonedDateTime expirationDate, boolean isBlocked) {
    log.debug("isBlockedAction:: parameters expirationDate: {}, isBlocked: {}", expirationDate,
      isBlocked);

    var result = isBlocked && (expirationDate == null
      || isAfterMillis(expirationDate, getZonedDateTime()));
    log.info("isBlockedAction: result {}", result);
    return result;
  }
}
