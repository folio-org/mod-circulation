package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.allOf;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.AccountRepository;
import org.folio.circulation.domain.FeeFineAccountAndAction;
import org.folio.circulation.domain.FeeFineActionRepository;
import org.folio.circulation.domain.representations.AccountStorageRepresentation;
import org.folio.circulation.domain.representations.FeeFineActionStorageRepresentation;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.results.CommonFailures;

public class FeeFineService {
  private final AccountRepository accountRepository;
  private final FeeFineActionRepository feeFineActionRepository;

  public FeeFineService(Clients clients) {
    this.accountRepository = new AccountRepository(clients);
    this.feeFineActionRepository = new FeeFineActionRepository(clients);
  }

  public CompletableFuture<Result<Void>> createAccountsAndActions(
    Collection<FeeFineAccountAndAction> accountAndActions) {

    return allOf(accountAndActions.stream()
      .map(this::createAccountAndAction)
      .toArray(CompletableFuture[]::new))
      .thenApply(notUsed -> Result.<Void>succeeded(null))
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<Void>> createAccountAndAction(FeeFineAccountAndAction accountAndAction) {
    final AccountStorageRepresentation account = new AccountStorageRepresentation(
      accountAndAction.getLoan(),
      accountAndAction.getItem(),
      accountAndAction.getFeeFineOwner(),
      accountAndAction.getFeeFine(),
      accountAndAction.getAmount());

    final FeeFineActionStorageRepresentation action = FeeFineActionStorageRepresentation.builder()
      .withCreatedBy(accountAndAction.getCreatedBy())
      .withCreatedAt(accountAndAction.getCreatedAt())
      .withBalance(accountAndAction.getAmount().doubleValue())
      .withAmount(accountAndAction.getAmount().doubleValue())
      .withUserId(accountAndAction.getLoan().getUserId())
      .withFeeFineType(accountAndAction.getFeeFine().getFeeFineType())
      .withAccountId(account.getId())
      .build();

    return accountRepository.create(account)
      .thenCompose(r -> r.after(notUsed -> feeFineActionRepository.create(action)))
      .thenApply(r -> r.map(null));
  }
}
