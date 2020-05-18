package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.StoredFeeFineAction.StoredFeeFineActionBuilder;
import static org.folio.circulation.services.feefine.FeeRefundProcessor.createLostItemFeeRefundProcessor;
import static org.folio.circulation.support.Result.failed;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.FeeFineActionRepository;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.representations.StoredAccount;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.services.feefine.AccountRefundContext;
import org.folio.circulation.services.feefine.FeeRefundProcessor;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.services.support.RefundAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.CommonFailures;

import io.vertx.core.json.JsonObject;

public class FeeFineService {
  private final AccountRepository accountRepository;
  private final FeeFineActionRepository feeFineActionRepository;
  private final UserRepository userRepository;
  private final ServicePointRepository servicePointRepository;
  private final FeeRefundProcessor lostItemRefundProcessor;

  public FeeFineService(Clients clients) {
    this.accountRepository = new AccountRepository(clients);
    this.feeFineActionRepository = new FeeFineActionRepository(clients);
    this.userRepository = new UserRepository(clients);
    this.servicePointRepository = new ServicePointRepository(clients);
    this.lostItemRefundProcessor = createLostItemFeeRefundProcessor();
  }

  public CompletableFuture<Result<Void>> createAccounts(
    Collection<CreateAccountCommand> accountAndActions) {

    return allOf(accountAndActions.stream()
      .map(this::createAccount)
      .toArray(CompletableFuture[]::new))
      .thenApply(Result::succeeded)
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
    return fetchUser(creation.getStaffUserId())
      .thenApply(r -> r.map(builder::withCreatedBy))
      .thenCompose(r -> r.after(notUsed -> fetchServicePoint(creation.getCurrentServicePointId())))
      .thenApply(r -> r.map(servicePoint -> builder
        .withBalance(createdAccount.getRemaining())
        .withAmount(createdAccount.getAmount())
        .withUserId(createdAccount.getUserId())
        .withAction(createdAccount.getFeeFineType())
        .withAccountId(createdAccount.getId())
        .withCreatedAt(servicePoint.getName())
        .build()))
      .thenCompose(r -> r.after(feeFineActionRepository::create))
      .thenApply(r -> r.map(notUsed -> null));
  }

  public CompletableFuture<Result<Void>> refundAndCloseAccounts(List<RefundAccountCommand> accounts) {
    return allOf(accounts.stream()
      .map(this::refundAndCloseAccount)
      .toArray(CompletableFuture[]::new))
      .thenApply(Result::succeeded)
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<Void>> refundAndCloseAccount(RefundAccountCommand refund) {
    final AccountRefundContext context = new AccountRefundContext(refund.getAccountToRefund());

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

  private CompletableFuture<Result<User>> fetchUser(String userId) {
    return userRepository.getUser(userId);
  }

  private CompletableFuture<Result<ServicePoint>> fetchServicePoint(String servicePointId) {
    return servicePointRepository.getServicePointById(servicePointId)
      // To prevent NPE.
      .thenApply(r -> r.map(sp -> sp == null ? new ServicePoint(new JsonObject()) : sp));
  }
}
