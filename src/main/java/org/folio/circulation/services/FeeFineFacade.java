package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.representations.StoredFeeFineAction.StoredFeeFineActionBuilder;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.representations.StoredAccount;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.feefine.AccountActionResponse;
import org.folio.circulation.services.feefine.CancelAccountCommand;
import org.folio.circulation.services.feefine.RefundAccountCommand;
import org.folio.circulation.services.feefine.FeeFineService;
import org.folio.circulation.services.support.RefundAndCancelAccountCommand;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;

public class FeeFineFacade {
  private static final Logger log = getLogger(FeeFineFacade.class);

  private final AccountRepository accountRepository;
  private final FeeFineActionRepository feeFineActionRepository;
  private final UserRepository userRepository;
  private final ServicePointRepository servicePointRepository;
  private final FeeFineService feeFineService;

  public FeeFineFacade(Clients clients) {
    this.accountRepository = new AccountRepository(clients);
    this.feeFineActionRepository = new FeeFineActionRepository(clients);
    this.userRepository = new UserRepository(clients);
    this.servicePointRepository = new ServicePointRepository(clients);
    this.feeFineService = new FeeFineService(clients);
  }

  public CompletableFuture<Result<List<FeeFineAction>>> createAccounts(
    Collection<CreateAccountCommand> accountAndActions) {

    return allOf(accountAndActions, this::createAccount)
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<FeeFineAction>> createAccount(CreateAccountCommand creation) {
    final StoredAccount account = new StoredAccount(
      creation.getLoan(),
      creation.getItem(),
      creation.getFeeFineOwner(),
      creation.getFeeFine(),
      creation.getAmount());

    return accountRepository.create(account)
      .thenCompose(r -> r.after(createdAccount -> createAccountCreatedAction(createdAccount, creation)));
  }

  private CompletableFuture<Result<FeeFineAction>> createAccountCreatedAction(
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
      .thenCompose(r -> r.after(feeFineActionRepository::create));
  }

  CompletableFuture<Result<AccountActionResponse>> refundAccountIfNeeded(
    RefundAndCancelAccountCommand command, User user) {

    final Account account = command.getAccount();

    if (!account.hasPaidOrTransferredAmount()) {
      log.info("Account {} is not processed yet, no refunds have to be issued", account.getId());
      return ofAsync(() -> null);
    }

    log.info("Initiating refund for account {}", account.getId());

    final RefundAccountCommand refundCommand = RefundAccountCommand.builder()
      .account(account)
      .currentServicePointId(command.getServicePointId())
      .refundReason(command.getRefundReason())
      .userName(user.getPersonalName())
      .build();

    return feeFineService.refundAccount(refundCommand);
  }

  CompletableFuture<Result<AccountActionResponse>> cancelAccountIfNeeded(
    RefundAndCancelAccountCommand command, User user) {

    final Account account = command.getAccount();

    if (!account.getRemaining().hasAmount()) {
      log.info("Nothing to cancel for account {}", account.getId());
      return ofAsync(() -> null);
    }

    log.info("Initiating cancel for account {}", account.getId());

    final CancelAccountCommand cancelCommand = CancelAccountCommand.builder()
      .accountId(account.getId())
      .currentServicePointId(command.getServicePointId())
      .cancellationReason(command.getCancelReason())
      .userName(user.getPersonalName())
      .build();

    return feeFineService.cancelAccount(cancelCommand);
  }

  private CompletableFuture<Result<StoredFeeFineActionBuilder>> populateCreatedBy(
    StoredFeeFineActionBuilder builder, CreateAccountCommand command) {

    if (command.isCreatedByAutomatedProcess()) {
      return completedFuture(succeeded(builder.createdByAutomatedProcess()));
    }

    return userRepository.getUser(command.getStaffUserId())
      .thenApply(r -> r.map(builder::withCreatedBy));
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
