package org.folio.circulation.resources;

import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.representations.CheckOutByBarcodeDryRunRequest;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.http.OkapiPermissions;
import org.folio.circulation.support.http.server.JsonHttpResponse;
import org.folio.circulation.support.http.server.WebContext;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class CheckOutByBarcodeDryRunResource extends Resource {

  private final String rootPath;
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CheckOutByBarcodeResource checkOutByBarcodeResource;

  public CheckOutByBarcodeDryRunResource(String rootPath, HttpClient client,
    CheckOutByBarcodeResource checkOutByBarcodeResource) {

    super(client);
    this.rootPath = rootPath;
    this.checkOutByBarcodeResource = checkOutByBarcodeResource;
  }

  @Override
  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(rootPath, router);
    routeRegistration.create(this::dryRunCheckOut);
  }

  private void dryRunCheckOut(RoutingContext routingContext) {
    var context = new WebContext(routingContext);
    var request = CheckOutByBarcodeDryRunRequest.fromJson(
      routingContext.body().asJsonObject());
    var checkOutByBarcodeRequest = new CheckOutByBarcodeRequest(null,
      request.getItemBarcode(), request.getUserBarcode(), request.getProxyUserBarcode(),
      UUID.randomUUID().toString(), BlockOverrides.noOverrides(), null);
    var permissions = OkapiPermissions.from(new WebContext(routingContext).getHeaders());
    var errorHandler = new OverridingErrorHandler(permissions);
    var clients = Clients.create(context, client);
    final var patronNoticePolicyRepository = new PatronNoticePolicyRepository(clients);

    checkOutByBarcodeResource.checkOut(checkOutByBarcodeRequest, routingContext, context,
      errorHandler, permissions, clients, true)
        .thenCompose(r -> r.after(records -> lookupNoticePolicyId(records,
          patronNoticePolicyRepository)))
        .thenApply(r -> r.next(this::mapToResponse))
        .thenApply(r -> r.map(JsonHttpResponse::created))
        .thenAccept(context::writeResultToHttpResponse);
  }

  private CompletableFuture<Result<LoanAndRelatedRecords>> lookupNoticePolicyId(
    LoanAndRelatedRecords records, PatronNoticePolicyRepository patronNoticePolicyRepository) {

    log.info("lookupNoticePolicyId:: lookup notice policy for item: {}", records.getItem().getItemId());

    return patronNoticePolicyRepository.lookupPolicyId(records.getItem(), records.getUser())
      .thenApply(r -> r.map(circRuleMatch -> records.getLoan().withPatronNoticePolicyId(
        circRuleMatch.getPolicyId())))
      .thenApply(r -> r.map(records::withLoan));
  }

  private Result<JsonObject> mapToResponse(LoanAndRelatedRecords records) {
    Loan loan = records.getLoan();
    JsonObject jsonResponse = new JsonObject();
    jsonResponse.put("loanPolicyId", loan.getLoanPolicyId());
    jsonResponse.put("overdueFinePolicyId", loan.getOverdueFinePolicyId());
    jsonResponse.put("lostItemPolicyId", loan.getLostItemPolicyId());
    jsonResponse.put("patronNoticePolicyId", loan.getPatronNoticePolicyId());
    log.info("mapToResponse:: result: {}", () -> jsonResponse);

    return succeeded(jsonResponse);
  }
}
