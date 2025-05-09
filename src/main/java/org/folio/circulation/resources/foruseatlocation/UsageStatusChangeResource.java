package org.folio.circulation.resources.foruseatlocation;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.resources.Resource;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.storage.SingleOpenLoanByUserAndItemBarcodeFinder;
import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.results.Result;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.resources.handlers.error.CirculationErrorType.FAILED_TO_FIND_SINGLE_OPEN_LOAN;
import static org.folio.circulation.resources.foruseatlocation.ChangeUsageStatusByBarcodeRequest.usageStatusChangeRequestFrom;

public abstract class UsageStatusChangeResource extends Resource {

  protected final String rootPath;

  public UsageStatusChangeResource(String rootPath, HttpClient client) {
    super(client);
    this.rootPath = rootPath;
  }

  protected CompletableFuture<Result<Loan>> findLoan(JsonObject request,
                                                     LoanRepository loanRepository, ItemRepository itemRepository, UserRepository userRepository,
                                                     CirculationErrorHandler errorHandler) {

    final SingleOpenLoanByUserAndItemBarcodeFinder finder
      = new SingleOpenLoanByUserAndItemBarcodeFinder(loanRepository,
      itemRepository, userRepository);

    return usageStatusChangeRequestFrom(request).after(shelfRequest ->
      finder.findLoan(shelfRequest.getItemBarcode(), shelfRequest.getUserBarcode())
        .thenApply(r -> errorHandler.handleValidationResult(r, FAILED_TO_FIND_SINGLE_OPEN_LOAN,
          (Loan) null)));
  }

  protected HttpResponse toResponse(JsonObject body) {
    return JsonHttpResponse.ok(body,
      String.format("/circulation/loans/%s", body.getString("id")));
  }


}
