package org.folio.circulation.resources;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.storage.SingleOpenLoanByUserAndItemBarcodeFinder;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class RenewByBarcodeResource extends RenewalResource {
  public RenewByBarcodeResource(HttpClient client) {
    super(client, "/circulation/renew-by-barcode");
  }

  @Override
  protected CompletableFuture<Result<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository,
    RequestQueueRepository requestQueueRepository) {


    return new SingleOpenLoanByUserAndItemBarcodeFinder()
      .findLoan(
        request,
        loanRepository,
        itemRepository,
        userRepository,
        requestQueueRepository
      );
  }
}
