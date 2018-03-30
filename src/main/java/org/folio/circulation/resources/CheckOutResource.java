package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanValidation;
import org.folio.circulation.domain.UserFetcher;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.UUID;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.LoanValidation.defaultStatusAndAction;

public class CheckOutResource extends CollectionResource {
  public CheckOutResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration("/circulation/check-out", router);

    routeRegistration.create(this::checkOut);
  }

  private void checkOut(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);
    final Clients clients = Clients.create(context, client);

    final UserFetcher userFetcher = new UserFetcher(clients);
    final InventoryFetcher inventoryFetcher = new InventoryFetcher(clients);

    JsonObject request = routingContext.getBodyAsJson();

    final JsonObject loan = new JsonObject();
    loan.put("id", UUID.randomUUID().toString());

    defaultStatusAndAction(loan);

    loan.put("loanDate", DateTime.now().toDateTime(DateTimeZone.UTC)
      .toString(ISODateTimeFormat.dateTime()));

    final String userBarcode = request.getString("userBarcode");
    final String itemBarcode = request.getString("itemBarcode");

    completedFuture(HttpResult.success(new LoanAndRelatedRecords(loan)))
      .thenCombineAsync(userFetcher.getUserByBarcode(userBarcode), this::addUser)
      .thenCombineAsync(inventoryFetcher.fetchByBarcode(itemBarcode), this::addInventoryRecords)
      .thenApply(r -> r.next(v -> LoanValidation.refuseWhenItemBarcodeDoesNotExist(r, itemBarcode)))
      .thenApply(r -> r.map(toLoan()))
      .thenApply(CreatedJsonHttpResult::from)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private Function<LoanAndRelatedRecords, JsonObject> toLoan() {
    return loanAndRelatedRecords -> {
      final JsonObject loan = loanAndRelatedRecords.getLoan();

      loan.put("userId", loanAndRelatedRecords.requestingUser.getString("id"));
      loan.put("itemId", loanAndRelatedRecords.inventoryRecords.item.getString("id"));

      return loan;
    };
  }

  private HttpResult<LoanAndRelatedRecords> addUser(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<JsonObject> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withRequestingUser);
  }

  private HttpResult<LoanAndRelatedRecords> addInventoryRecords(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<InventoryRecords> inventoryRecordsResult) {

    return HttpResult.combine(loanResult, inventoryRecordsResult,
      LoanAndRelatedRecords::withInventoryRecords);
  }
}
