package org.folio.circulation.domain.validation;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.folio.circulation.domain.AutomatedPatronBlock;
import org.folio.circulation.domain.AutomatedPatronBlocks;
import org.folio.circulation.infrastructure.storage.AutomatedPatronBlocksRepository;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.error.ValidationError;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.ValidationErrorFailure;

public class AutomatedPatronBlocksValidator {
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

    return refuse(loanAndRelatedRecords.getLoan().getUserId(),
      AutomatedPatronBlock::isBlockBorrowing, loanAndRelatedRecords);
  }

  public CompletableFuture<Result<RenewalContext>>
  refuseWhenRenewalActionIsBlockedForPatron(RenewalContext renewalContext) {

    return refuse(renewalContext.getLoan().getUserId(),
      AutomatedPatronBlock::isBlockRenewal, renewalContext);
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>>
  refuseWhenRequestActionIsBlockedForPatron(RequestAndRelatedRecords requestAndRelatedRecords) {

    return refuse(requestAndRelatedRecords.getUserId(), AutomatedPatronBlock::isBlockRequest,
      requestAndRelatedRecords);
  }

  private <T> CompletableFuture<Result<T>> refuse(String userId,
    Predicate<AutomatedPatronBlock> actionPredicate, T mapTo) {

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

    if (automatedPatronBlocks == null) {
      return ofAsync(ArrayList::new);
    }

    return completedFuture(succeeded(automatedPatronBlocks.getBlocks().stream()
      .filter(actionPredicate)
      .collect(Collectors.toList())))
      .exceptionally(throwable -> succeeded(new ArrayList<>()));
  }

  private CompletableFuture<Result<Boolean>> blocksExist(
    List<AutomatedPatronBlock> automatedPatronBlocks) {

    return completedFuture(succeeded(!automatedPatronBlocks.isEmpty()));
  }
}
