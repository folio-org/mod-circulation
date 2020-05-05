package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.allOf;
import static org.folio.circulation.domain.representations.AccountStorageRepresentation.fromAccount;
import static org.folio.circulation.domain.representations.FeeFinePaymentAction.CANCELLED_ITEM_RETURNED;
import static org.folio.circulation.support.Result.combined;
import static org.folio.circulation.support.Result.succeeded;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.services.support.AccountCreation;
import org.folio.circulation.domain.FeeFineActionRepository;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.ServicePointRepository;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.representations.AccountStorageRepresentation;
import org.folio.circulation.domain.representations.FeeFineActionStorageRepresentation;
import org.folio.circulation.services.support.AccountCancellation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.results.CommonFailures;

import io.vertx.core.json.JsonObject;

public class FeeFineService {
  private final AccountRepository accountRepository;
  private final FeeFineActionRepository feeFineActionRepository;
  private final UserRepository userRepository;
  private final ServicePointRepository servicePointRepository;

  public FeeFineService(Clients clients) {
    this.accountRepository = new AccountRepository(clients);
    this.feeFineActionRepository = new FeeFineActionRepository(clients);
    this.userRepository = new UserRepository(clients);
    this.servicePointRepository = new ServicePointRepository(clients);
  }

  public CompletableFuture<Result<Void>> createAccountsAndActions(
    Collection<AccountCreation> accountAndActions) {

    return allOf(accountAndActions.stream()
      .map(this::createAccountAndAction)
      .toArray(CompletableFuture[]::new))
      .thenApply(Result::succeeded)
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<Void>> createAccountAndAction(AccountCreation creation) {
    final AccountStorageRepresentation account = new AccountStorageRepresentation(
      creation.getLoan(),
      creation.getItem(),
      creation.getFeeFineOwner(),
      creation.getFeeFine(),
      creation.getAmount());

    return accountRepository.create(account)
      .thenCompose(r -> r.after(createdAccount -> createAccountCreatedAction(createdAccount, creation)));
  }

  private CompletableFuture<Result<Void>> createAccountCreatedAction(Account createdAccount, AccountCreation creation) {
    return fetchActionReferenceData(creation.getStaffUserId(), creation.getCurrentServicePointId())
      .thenApply(r -> r.map(referenceData -> FeeFineActionStorageRepresentation.builder()
        .withCreatedBy(referenceData.getLeft())
        .withCreatedAt(referenceData.getRight().getName())
        .withBalance(createdAccount.getRemaining())
        .withAmount(createdAccount.getAmount())
        .withUserId(createdAccount.getUserId())
        .withAction(createdAccount.getFeeFineType())
        .withAccountId(createdAccount.getId())
        .build()))
      .thenCompose(r -> r.after(feeFineActionRepository::create))
      .thenApply(r -> r.map(notUsed -> null));
  }

  public CompletableFuture<Result<Void>> cancelAccounts(List<AccountCancellation> toCancel) {
    return allOf(toCancel.stream()
      .map(this::cancelAccount)
      .toArray(CompletableFuture[]::new))
      .thenApply(Result::succeeded)
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<Void>> cancelAccount(AccountCancellation accountCancellation) {
    return createAccountCanceledAction(accountCancellation)
      .thenCompose(r -> r.after(feeFineActionRepository::create))
      .thenCompose(r -> r.after(notUsed -> closeAccount(accountCancellation)));
  }

  private CompletableFuture<Result<FeeFineActionStorageRepresentation>> createAccountCanceledAction(
    AccountCancellation cancellation) {

    return fetchActionReferenceData(cancellation.getStaffUserId(), cancellation.getServicePointId())
      .thenApply(r -> r.map(referenceData -> FeeFineActionStorageRepresentation.builder()
        .useAccount(cancellation.getAccountToCancel())
        .withAction(CANCELLED_ITEM_RETURNED)
        .withAmount(cancellation.getAccountToCancel().getRemaining())
        .withBalance(BigDecimal.ZERO)
        .withCreatedAt(referenceData.getRight().getName())
        .withCreatedBy(referenceData.getLeft())
        .build()
      ));
  }

  private CompletableFuture<Result<Void>> closeAccount(AccountCancellation cancellation) {
    final AccountStorageRepresentation accountToClose = fromAccount(
      cancellation.getAccountToCancel());

    accountToClose.close(cancellation.getCancellationReason());

    return accountRepository.update(accountToClose);
  }

  private CompletableFuture<Result<Pair<User, ServicePoint>>> fetchActionReferenceData(
    String userId, String servicePointId) {

    return userRepository.getUser(userId)
      .thenCombineAsync(servicePointRepository.getServicePointById(servicePointId),
        combined((user, servicePoint) -> succeeded(new ImmutablePair<>(user, servicePoint))))
      .thenApply(r -> r.map(pair -> pair.getRight() == null
        // Return a wrapper to prevent NPE
        ? new ImmutablePair<>(pair.getLeft(), new ServicePoint(new JsonObject()))
        : pair));
  }
}
