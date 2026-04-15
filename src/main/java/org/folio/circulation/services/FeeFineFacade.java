package org.folio.circulation.services;

import static java.util.Optional.ofNullable;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.AsyncCoordinationUtil.allOf;
import static org.folio.circulation.support.results.Result.emptyAsync;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.ActualCostFeeCancelReason;
import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.representations.StoredAccount;
import org.folio.circulation.domain.representations.StoredFeeFineAction;
import org.folio.circulation.domain.representations.StoredFeeFineAction.StoredFeeFineActionBuilder;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.feesandfines.FeeFineActionRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.services.feefine.AccountActionResponse;
import org.folio.circulation.services.feefine.CancelAccountCommand;
import org.folio.circulation.services.feefine.CancelActualCostFeeRequest;
import org.folio.circulation.services.feefine.FeeFineService;
import org.folio.circulation.services.feefine.RefundAccountCommand;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.services.support.RefundAndCancelAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class FeeFineFacade {
  private static final Logger log = LogManager.getLogger(FeeFineFacade.class);

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
    Collection<CreateAccountCommand> commands) {

    log.info("createAccounts:: parameters commands count: {}", commands::size);
    return allOf(commands, this::createAccount)
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  public CompletableFuture<Result<FeeFineAction>> createAccount(CreateAccountCommand command) {
    log.info("createAccount:: parameters loanId: {}",
      command.getLoan() != null ? command.getLoan().getId() : "null");
    return ofAsync(() -> new StoredAccount(command))
      .thenCompose(r -> r.after(accountRepository::create))
      .thenCompose(r -> r.after(account -> createFeeFineChargeAction(account, command)))
      .exceptionally(CommonFailures::failedDueToServerError);
  }

  private CompletableFuture<Result<FeeFineAction>> createFeeFineChargeAction(Account account,
    CreateAccountCommand command) {

    log.info("createFeeFineChargeAction:: parameters accountId: {}", account::getId);
    return ofAsync(() -> StoredFeeFineAction.builder(account))
      .thenCompose(r -> r.after(builder -> populateCreatedBy(builder, command)))
      .thenCompose(r -> r.after(builder -> populateCreatedAt(builder, command)))
      .thenApply(r -> r.map(StoredFeeFineActionBuilder::build))
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
    RefundAndCancelAccountCommand command, User user, AccountActionResponse refundResponse) {

    final Account account = command.getAccount();

    FeeAmount currentRemainingAmount = refundResponse != null
      ? refundResponse.getRemainingAmount()
      : account.getRemaining();

    if (!currentRemainingAmount.hasAmount()) {
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

    log.info("populateCreatedBy:: createdByAutomatedProcess: {}", command::isCreatedByAutomatedProcess);
    if (command.isCreatedByAutomatedProcess()) {
      return completedFuture(succeeded(builder.createdByAutomatedProcess()));
    }

    return userRepository.getUser(command.getStaffUserId())
      .thenApply(r -> r.map(builder::withCreatedBy));
  }

  private CompletableFuture<Result<ServicePoint>> fetchServicePoint(String servicePointId) {
    log.info("fetchServicePoint:: parameters servicePointId: {}", servicePointId);
    return servicePointRepository.getServicePointById(servicePointId);
  }

  private CompletableFuture<Result<StoredFeeFineActionBuilder>> populateCreatedAt(
    StoredFeeFineActionBuilder builder, CreateAccountCommand command) {

    log.info("populateCreatedAt:: createdByAutomatedProcess: {}", command::isCreatedByAutomatedProcess);
    if (command.isCreatedByAutomatedProcess()) {
      return completedFuture(succeeded(builder));
    }

    return fetchServicePoint(command.getCurrentServicePointId())
      .thenApply(r -> r.map(builder::withCreatedAt));
  }

  public CompletableFuture<Result<ActualCostRecord>> cancelActualCostFee(
    ActualCostRecord actualCostRecord, ActualCostFeeCancelReason reason) {

    if (actualCostRecord == null) {
      log.info("cancelActualCostFee:: actual cost record is null, aborting");
      return emptyAsync();
    }

    String cancellationReason = ofNullable(reason)
      .map(ActualCostFeeCancelReason::getValue)
      .orElse(null);

    CancelActualCostFeeRequest request = CancelActualCostFeeRequest.builder()
      .actualCostRecordId(actualCostRecord.getId())
      .additionalInfoForStaff(cancellationReason)
      .build();

    return feeFineService.cancelActualCostFeeFine(request);
  }
}
