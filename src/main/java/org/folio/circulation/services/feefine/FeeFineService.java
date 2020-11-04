package org.folio.circulation.services.feefine;

import static org.folio.circulation.support.http.ResponseMapping.forwardOnFailure;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;

public class FeeFineService {
  private static final Logger log = getLogger(FeeFineService.class);

  private final CollectionResourceClient accountRefundClient;
  private final CollectionResourceClient accountCancelClient;

  public FeeFineService(Clients clients) {
    this.accountCancelClient = clients.accountsCancelClient();
    this.accountRefundClient = clients.accountsRefundClient();
  }

  public CompletableFuture<Result<Void>> refundAccount(AccountRefundCommand refundCommand) {
    if (!refundCommand.hasPaidAndTransferredAmount()) {
      log.info("Account has nothing to refund {}", refundCommand.getAccountId());
      return ofAsync(() -> null);
    }

    final AccountActionRequest refundRequest = AccountActionRequest.builder()
      .amount(refundCommand.getAccount().getPaidAndTransferredAmount())
      .servicePointId(refundCommand.getCurrentServicePointId())
      .userName(refundCommand.getUserName())
      .reason(refundCommand.getRefundReason().getValue())
      .build();

    return accountRefundClient.post(refundRequest.toJson(), refundCommand.getAccountId())
      .thenApply(voidCreatedInterpreter()::flatMap);
  }

  public CompletableFuture<Result<Void>> cancelAccount(AccountCancelCommand cancelCommand) {
    final AccountActionRequest cancelRequest = AccountActionRequest.builder()
      .amount(cancelCommand.getAccount().getRemaining())
      .servicePointId(cancelCommand.getCurrentServicePointId())
      .userName(cancelCommand.getUserName())
      .reason(cancelCommand.getCancellationReason().getValue())
      .build();

    return accountCancelClient.post(cancelRequest.toJson(), cancelCommand.getAccountId())
      .thenApply(voidCreatedInterpreter()::flatMap);
  }

  private ResponseInterpreter<Void> voidCreatedInterpreter() {
    return new ResponseInterpreter<Void>()
      .on(201, succeeded(null))
      .otherwise(forwardOnFailure());
  }
}
