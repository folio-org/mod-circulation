package org.folio.circulation.resources.renewal;

import static org.folio.circulation.resources.renewal.RenewByBarcodeRequest.renewalRequestFrom;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.storage.SingleOpenLoanByUserAndItemBarcodeFinder;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;

public class OverrideRenewalByBarcodeResource extends RenewalResource {

  public OverrideRenewalByBarcodeResource(HttpClient client) {
    super("/circulation/override-renewal-by-barcode", new OverrideRenewalStrategy(),
      new OverrideRenewalFeeProcessingStrategy(), client);
  }

  @Override
  protected CompletableFuture<Result<Loan>> findLoan(JsonObject request,
    LoanRepository loanRepository, ItemRepository itemRepository, UserRepository userRepository,
    CirculationErrorHandler errorHandler) {

    final SingleOpenLoanByUserAndItemBarcodeFinder finder
      = new SingleOpenLoanByUserAndItemBarcodeFinder(loanRepository,
      itemRepository, userRepository);

    return renewalRequestFrom(request)
      .after(renewal -> finder.findLoan(renewal.getItemBarcode(), renewal.getUserBarcode()));
  }
}
