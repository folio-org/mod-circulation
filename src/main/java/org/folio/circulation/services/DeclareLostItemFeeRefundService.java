package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.CommonFailures;
import org.folio.circulation.support.results.Result;

public class DeclareLostItemFeeRefundService extends LostItemFeeRefundService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public DeclareLostItemFeeRefundService(Clients clients, ItemRepository itemRepository,
    UserRepository userRepository, LoanRepository loanRepository) {

    super(clients, itemRepository, userRepository, loanRepository);
  }

  @Override
  public CompletableFuture<Result<LostItemFeeRefundContext>> refundLostItemFees(
    LostItemFeeRefundContext refundFeeContext) {

    log.info("refundLostItemFees:: attempting to refund lost item fees: loanId={}, cancelReason={}",
      refundFeeContext::getLoanId, refundFeeContext::getCancelReason);

    if (!refundFeeContext.shouldRefundFeesForItem()) {
      log.info("refundLostItemFees:: no need to refund fees for loan {}", refundFeeContext::getLoanId);
      return completedFuture(succeeded(refundFeeContext));
    }

    return lookupLoan(succeeded(refundFeeContext))
      .thenCompose(r -> r.after(this::processRefund))
      .exceptionally(CommonFailures::failedDueToServerError);
  }
}
