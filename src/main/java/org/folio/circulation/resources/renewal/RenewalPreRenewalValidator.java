package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.override.OverridableBlockType.PATRON_BLOCK;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FETCH_USER;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FIND_SINGLE_OPEN_LOAN;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.ITEM_DOES_NOT_EXIST;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.RENEWAL_IS_BLOCKED;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_AUTOMATICALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_BLOCKED_MANUALLY;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.USER_IS_INACTIVE;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.validation.AutomatedPatronBlocksValidator;
import org.folio.circulation.domain.validation.InactiveUserRenewalValidator;
import org.folio.circulation.domain.validation.RenewalOfItemsWithReminderFeesValidator;
import org.folio.circulation.domain.validation.UserManualBlocksValidator;
import org.folio.circulation.domain.validation.Validator;
import org.folio.circulation.domain.validation.overriding.BlockValidator;
import org.folio.circulation.domain.validation.overriding.OverridingBlockValidator;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

final class RenewalPreRenewalValidator {
  private RenewalPreRenewalValidator() {
  }

  static CompletableFuture<Result<RenewalContext>> refuseWhenPatronIsInactive(
    Result<RenewalContext> result, CirculationErrorHandler errorHandler,
    CirculationErrorType errorType) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
      FAILED_TO_FETCH_USER)) {

      return completedFuture(result);
    }

    Validator<RenewalContext> validator = new BlockValidator<>(USER_IS_INACTIVE,
      new InactiveUserRenewalValidator()::refuseWhenPatronIsInactive);

    return result.after(renewalContext -> validator.validate(renewalContext)
      .thenApply(r -> errorHandler.handleValidationResult(r, errorType, result)));
  }

  static Validator<RenewalContext> createAutomatedPatronBlocksValidator(
    JsonObject request, OkapiPermissions permissions,
    AutomatedPatronBlocksRepository automatedPatronBlocksRepository) {

    Function<RenewalContext, CompletableFuture<Result<RenewalContext>>> validationFunction =
      new AutomatedPatronBlocksValidator(automatedPatronBlocksRepository)
        ::refuseWhenRenewalActionIsBlockedForPatron;

    BlockOverrides blockOverrides = getOverrideBlocks(request);

    return blockOverrides.getPatronBlockOverride().isRequested()
      ? new OverridingBlockValidator<>(PATRON_BLOCK, blockOverrides, permissions)
      : new BlockValidator<>(USER_IS_BLOCKED_AUTOMATICALLY, validationFunction);
  }

  static Validator<RenewalContext> createManualPatronBlocksValidator(
    JsonObject request, OkapiPermissions permissions, Clients clients) {

    Function<RenewalContext, CompletableFuture<Result<RenewalContext>>> validationFunction =
      new UserManualBlocksValidator(clients)::refuseWhenUserIsBlocked;

    BlockOverrides blockOverrides = getOverrideBlocks(request);

    return blockOverrides.getPatronBlockOverride().isRequested()
      ? new OverridingBlockValidator<>(PATRON_BLOCK, blockOverrides, permissions)
      : new BlockValidator<>(USER_IS_BLOCKED_MANUALLY, validationFunction);
  }

  static CompletableFuture<Result<RenewalContext>> refuseWhenRenewalActionIsBlockedForPatron(
    Validator<RenewalContext> validator, Result<RenewalContext> result,
    CirculationErrorHandler errorHandler, CirculationErrorType errorType) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
      FAILED_TO_FETCH_USER)) {

      return completedFuture(result);
    }

    return result.after(renewalContext -> validator.validate(renewalContext)
      .thenApply(r -> errorHandler.handleValidationResult(r, errorType, result)));
  }

  static CompletableFuture<Result<RenewalContext>> blockRenewalOfItemsWithReminderFees(
    RenewalContext context, CirculationErrorHandler errorHandler) {

    if (errorHandler.hasAny(ITEM_DOES_NOT_EXIST, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
      FAILED_TO_FETCH_USER)) {

      return completedFuture(Result.succeeded(context));
    }

    Validator<RenewalContext> validator = new BlockValidator<>(RENEWAL_IS_BLOCKED,
      new RenewalOfItemsWithReminderFeesValidator()
        ::blockRenewalIfReminderFeesExistAndDisallowRenewalWithReminders);

    return validator.validate(context)
      .thenApply(r -> errorHandler.handleValidationResult(r, RENEWAL_IS_BLOCKED, r));
  }

  private static BlockOverrides getOverrideBlocks(JsonObject request) {
    return BlockOverrides.from(getObjectProperty(request, "overrideBlocks"));
  }
}
