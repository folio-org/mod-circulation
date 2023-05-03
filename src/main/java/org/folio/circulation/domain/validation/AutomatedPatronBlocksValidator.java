package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.listAsString;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.AutomatedPatronBlock;
import org.folio.circulation.domain.AutomatedPatronBlocks;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class AutomatedPatronBlocksValidator {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final AutomatedPatronBlocksRepository automatedPatronBlocksRepository;
  private final Function<List<String>, ValidationErrorFailure> actionIsBlockedForPatronErrorFunction;

  public AutomatedPatronBlocksValidator(
    AutomatedPatronBlocksRepository automatedPatronBlocksRepository,
    Function<List<String>, ValidationErrorFailure> actionIsBlockedForPatronErrorFunction) {

    this.automatedPatronBlocksRepository = automatedPatronBlocksRepository;
    this.actionIsBlockedForPatronErrorFunction = actionIsBlockedForPatronErrorFunction;
  }

  public AutomatedPatronBlocksValidator(AutomatedPatronBlocksRepository automatedPatronBlocksRepository) {
    this(automatedPatronBlocksRepository, messages -> new ValidationErrorFailure(messages.stream()
      .map(message -> new ValidationError(message, new HashMap<>()))
      .collect(Collectors.toList())));
  }

  public AutomatedPatronBlocksValidator(Clients clients) {
    this(new AutomatedPatronBlocksRepository(clients));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>>
  refuseWhenCheckOutActionIsBlockedForPatron(LoanAndRelatedRecords loanAndRelatedRecords) {
    log.debug("refuseWhenCheckOutActionIsBlockedForPatron:: parameters loanAndRelatedRecords={}",
      loanAndRelatedRecords);

    return refuse(loanAndRelatedRecords.getLoan().getUserId(),
      AutomatedPatronBlock::isBlockBorrowing, loanAndRelatedRecords);
  }

  public CompletableFuture<Result<RenewalContext>>
  refuseWhenRenewalActionIsBlockedForPatron(RenewalContext renewalContext) {
    log.debug("refuseWhenRenewalActionIsBlockedForPatron:: parameters renewalContext={}",
      renewalContext);

    return refuse(renewalContext.getLoan().getUserId(),
      AutomatedPatronBlock::isBlockRenewal, renewalContext);
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>>
  refuseWhenRequestActionIsBlockedForPatron(RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("refuseWhenRequestActionIsBlockedForPatron:: parameters requestAndRelatedRecords={}",
      requestAndRelatedRecords);

    return refuse(requestAndRelatedRecords.getUserId(), AutomatedPatronBlock::isBlockRequest,
      requestAndRelatedRecords);
  }

  private <T> CompletableFuture<Result<T>> refuse(String userId,
    Predicate<AutomatedPatronBlock> actionPredicate, T mapTo) {

    log.debug("refuse:: parameters userId={}, actionPredicate, mapTo", userId);

    return ofAsync(() -> userId)
      .thenComposeAsync(r -> r.after(automatedPatronBlocksRepository::findByUserId))
      .thenComposeAsync(r -> r.after(blocks -> getActionBlock(blocks, actionPredicate)))
      .thenComposeAsync(result -> result.failAfter(this::blocksExist,
        blockList -> actionIsBlockedForPatronErrorFunction.apply(
          blockList.stream()
            .map(AutomatedPatronBlock::getMessage)
            .collect(Collectors.toList())
        )))
      .thenApply(result -> result.map(v -> mapTo));
  }

  private CompletableFuture<Result<List<AutomatedPatronBlock>>> getActionBlock(
    AutomatedPatronBlocks automatedPatronBlocks, Predicate<AutomatedPatronBlock> actionPredicate) {

    log.debug("getActionBlock:: parameters automatedPatronBlocks={}, actionPredicate",
      automatedPatronBlocks);

    if (automatedPatronBlocks == null) {
      log.info("getActionBlock:: automatedPatronBlocks is null");
      return ofAsync(ArrayList::new);
    }

    return completedFuture(succeeded(automatedPatronBlocks.getBlocks().stream()
      .filter(actionPredicate)
      .collect(Collectors.toList())))
      .exceptionally(throwable -> succeeded(new ArrayList<>()));
  }

  private CompletableFuture<Result<Boolean>> blocksExist(
    List<AutomatedPatronBlock> automatedPatronBlocks) {

    log.debug("blocksExist:: parameters automatedPatronBlocks={}",
      () -> listAsString(automatedPatronBlocks));

    return completedFuture(succeeded(!automatedPatronBlocks.isEmpty()));
  }
}
