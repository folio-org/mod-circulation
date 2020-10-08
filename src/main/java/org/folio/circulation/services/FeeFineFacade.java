package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.StoredFeeFineAction.StoredFeeFineActionBuilder;
import static org.folio.circulation.services.feefine.FeeRefundProcessor.createLostItemFeeRefundProcessor;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.representations.StoredAccount;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.feefine.AccountRefundContext;
import org.folio.circulation.services.feefine.FeeRefundProcessor;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.services.support.RefundAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

import lombok.val;

public class FeeFineFacade {
  private final AccountRepository accountRepository;
  private final FeeFineActionRepository feeFineActionRepository;
  private final UserRepository userRepository;
  private final ServicePointRepository servicePointRepository;
  private final FeeRefundProcessor lostItemRefundProcessor;

  public FeeFineFacade(Clients clients) {
    this.accountRepository = new AccountRepository(clients);
    this.feeFineActionRepository = new FeeFineActionRepository(clients);
    this.userRepository = new UserRepository(clients);
    this.servicePointRepository = new ServicePointRepository(clients);
    this.lostItemRefundProcessor = createLostItemFeeRefundProcessor();
  }

  public CompletableFuture<Result<Void>> createAccounts(
    Collection<CreateAccountCommand> accountAndActions) {

    return allOf(accountAndActions, this::createAccount)
      .thenApply(r -> r.<Void>map(list -> null))
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<Void>> createAccount(CreateAccountCommand creation) {
    final StoredAccount account = new StoredAccount(
      creation.getLoan(),
      creation.getItem(),
      creation.getFeeFineOwner(),
      creation.getFeeFine(),
      creation.getAmount());

    return accountRepository.create(account)
      .thenCompose(r -> r.after(createdAccount -> createAccountCreatedAction(createdAccount, creation)));
  }

  private CompletableFuture<Result<Void>> createAccountCreatedAction(
    Account createdAccount, CreateAccountCommand creation) {

    final StoredFeeFineActionBuilder builder = StoredFeeFineAction.builder();
    return populateCreatedBy(builder, creation)
      .thenCompose(r -> r.after(updatedBuilder -> populateCreatedAt(updatedBuilder, creation)))
      .thenApply(r -> r.map(updatedBuilder -> updatedBuilder
        .withBalance(createdAccount.getRemaining())
        .withAmount(createdAccount.getAmount())
        .withUserId(createdAccount.getUserId())
        .withAction(createdAccount.getFeeFineType())
        .withAccountId(createdAccount.getId())
        .build()))
      .thenCompose(r -> r.after(feeFineActionRepository::create))
      .thenApply(r -> r.map(notUsed -> null));
  }

  public CompletableFuture<Result<Void>> refundAndCloseAccounts(List<RefundAccountCommand> accounts) {
    return allOf(accounts, this::refundAndCloseAccount)
      .thenApply(r -> r.<Void>map(list -> null))
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<Void>> refundAndCloseAccount(RefundAccountCommand refund) {
    val context = new AccountRefundContext(refund.getAccountToRefund(),
      refund.getCancellationReason());

    return fetchUser(refund.getStaffUserId())
      .thenApply(r -> r.map(context::withUser))
      .thenComposeAsync(r -> r.after(notUsed -> fetchServicePoint(refund.getServicePointId())))
      .thenApply(r -> r.map(context::withServicePoint))
      .thenCompose(r -> r.after(this::processRefundAndClose));
  }

  private CompletableFuture<Result<Void>> processRefundAndClose(AccountRefundContext context) {
    if (!lostItemRefundProcessor.canHandleAccountRefund(context.getAccount())) {
      return completedFuture(noRefundProcessorForFeeType(context.getAccount().getFeeFineType()));
    }

    return createRefundAndCloseActions(context)
      .thenCompose(r -> r.after(notUsed -> updateAccount(context)));
  }

  private Result<Void> noRefundProcessorForFeeType(String feeFineType) {
    return failed(new ServerErrorFailure(
      "No refund processor available for fee/fine of type: " + feeFineType));
  }

  private CompletableFuture<Result<Void>> updateAccount(AccountRefundContext context) {
    final Account account = context.getAccount();
    if (account.hasTransferredAmount()) {
      lostItemRefundProcessor.onTransferAmountRefundActionSaved(context);
    }

    if (account.hasPaidAmount()) {
      lostItemRefundProcessor.onPaidAmountRefundActionSaved(context);
    }

    if (account.hasRemainingAmount()) {
      lostItemRefundProcessor.onRemainingAmountActionSaved(context);
    }

    return accountRepository.update(StoredAccount.fromAccount(context.getAccount()));
  }

  private CompletableFuture<Result<Void>> createRefundAndCloseActions(
    AccountRefundContext context) {

    final Account account = context.getAccount();
    if (account.hasTransferredAmount()) {
      lostItemRefundProcessor.onHasTransferAmount(context);
    }

    if (account.hasPaidAmount()) {
      lostItemRefundProcessor.onHasPaidAmount(context);
    }

    if (account.hasRemainingAmount()) {
      lostItemRefundProcessor.onHasRemainingAmount(context);
    }

    return feeFineActionRepository.createAll(context.getActions());
  }

  private CompletableFuture<Result<StoredFeeFineActionBuilder>> populateCreatedBy(
    StoredFeeFineActionBuilder builder, CreateAccountCommand command) {

    if (command.isCreatedByAutomatedProcess()) {
      return completedFuture(succeeded(builder.createdByAutomatedProcess()));
    }

    return userRepository.getUser(command.getStaffUserId())
      .thenApply(r -> r.map(builder::withCreatedBy));
  }

  private CompletableFuture<Result<User>> fetchUser(String userId) {
    return userRepository.getUser(userId);
  }

  private CompletableFuture<Result<ServicePoint>> fetchServicePoint(String servicePointId) {
    return servicePointRepository.getServicePointById(servicePointId);
  }

  private CompletableFuture<Result<StoredFeeFineActionBuilder>> populateCreatedAt(
    StoredFeeFineActionBuilder builder, CreateAccountCommand command) {

    if (command.isCreatedByAutomatedProcess()) {
      return completedFuture(succeeded(builder));
    }

    return fetchServicePoint(command.getCurrentServicePointId())
      .thenApply(r -> r.map(builder::withCreatedAt));
  }
}
