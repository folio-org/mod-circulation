package org.folio.circulation.resources.renewal;

import static org.folio.circulation.resources.renewal.RenewByBarcodeRequest.renewalRequestFrom;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.LoanRepository;
import org.folio.circulation.infrastructure.storage.UserRepository;
import org.folio.circulation.storage.SingleOpenLoanByUserAndItemBarcodeFinder;
import org.folio.circulation.support.Result;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class RenewByBarcodeResource extends RenewalResource {
  public RenewByBarcodeResource(HttpClient client) {
    super("/circulation/renew-by-barcode", new RegularRenewalStrategy(),
      new RegularRenewalFeeProcessingStrategy(), client);
  }

  @Override
  protected CompletableFuture<Result<Loan>> findLoan(
    JsonObject request,
    LoanRepository loanRepository,
    ItemRepository itemRepository,
    UserRepository userRepository) {

    final SingleOpenLoanByUserAndItemBarcodeFinder finder
      = new SingleOpenLoanByUserAndItemBarcodeFinder(loanRepository,
      itemRepository, userRepository);

    return renewalRequestFrom(request).after(renewal ->
      finder.findLoan(renewal.getItemBarcode(), renewal.getUserBarcode()));
  }
}
