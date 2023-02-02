package api.support.fakes;

import static api.support.fakes.Storage.getStorage;
import static api.support.fakes.StorageSchema.validatorForFeeFineCancelOperationSchema;
import static api.support.fakes.StorageSchema.validatorForFeeFineOperationSchema;
import static org.folio.circulation.domain.ActualCostRecord.Status.CANCELLED;
import static org.folio.circulation.support.http.server.JsonHttpResponse.created;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.folio.rest.util.OkapiConnectionParams.OKAPI_TENANT_HEADER;

import java.util.Map;
import java.util.UUID;

import org.folio.circulation.infrastructure.serialization.JsonSchemaValidator;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.SneakyThrows;

public class FakeFeeFineOperationsModule {
  @SneakyThrows
  public void register(Router router) {
    router.post("/accounts/:accountId/refund")
      .handler(validateRequest(validatorForFeeFineOperationSchema()));
    router.post("/accounts/:accountId/refund").handler(this::refundAccount);

    router.post("/accounts/:accountId/cancel")
      .handler(validateRequest(validatorForFeeFineCancelOperationSchema()));
    router.post("/accounts/:accountId/cancel").handler(this::cancelAccount);
    router.post("/actual-cost-fee-fine/cancel").handler(this::cancelActualCostFee);
  }

  private void refundAccount(RoutingContext context) {
    final JsonObject account = getAccountById(context);

    final String accountId = context.pathParam("accountId");
    final double actionAmount = Double.parseDouble(context.getBodyAsJson().getString("amount"));
    final double accountAmount = account.getDouble("amount");
    final double accountRemainingAmount = account.getDouble("remaining");

    boolean isFullRefund = accountAmount == actionAmount;

    final String status = isFullRefund ? "Closed" : "Open";
    final String paymentStatus = isFullRefund ? "Refunded fully" : "Refunded partially";

    writeByPath(account, status, "status", "name");
    writeByPath(account,  paymentStatus, "paymentStatus", "name");

    updateAccount(context, account);

    String actionTypeForCredit = isFullRefund ? "Credited fully" : "Credited partially";

    JsonObject creditFeeFineAction = createFeeFineAction(context, account, actionTypeForCredit,
      actionAmount, accountRemainingAmount);

    JsonObject refundFeeFineAction = createFeeFineAction(context, account, paymentStatus,
      actionAmount, accountRemainingAmount + actionAmount);

    final JsonObject fakeRefundResponseJson = new JsonObject()
      .put("accountId", accountId)
      .put("amount", String.valueOf(accountAmount))
      .put("remainingAmount", refundFeeFineAction.getString("balance"))
      .put("feefineactions", new JsonArray()
        .add(creditFeeFineAction)
        .add(refundFeeFineAction)
      );

    created(fakeRefundResponseJson).writeTo(context.response());
  }

  private void cancelAccount(RoutingContext context) {
    final String cancellationReason = context.getBodyAsJson()
      .getString("cancellationReason", "Cancelled as error");
    final JsonObject account = getAccountById(context);
    final String accountId = context.pathParam("accountId");
    final double accountAmount = account.getDouble("amount");

    writeByPath(account, "Closed", "status", "name");
    writeByPath(account, cancellationReason, "paymentStatus", "name");
    write(account, "remaining", 0.0);

    updateAccount(context, account);

    JsonObject cancelFeeFineAction = createFeeFineAction(context, account, cancellationReason,
      accountAmount, 0.0);

    final JsonObject responseJson = new JsonObject()
      .put("accountId", accountId)
      .put("amount", String.valueOf(accountAmount))
      .put("feefineactions", new JsonArray().add(cancelFeeFineAction));

    created(responseJson).writeTo(context.response());
  }

  private void cancelActualCostFee(RoutingContext context) {
    JsonObject request = context.body().asJsonObject();

    JsonObject response = getActualCostRecordStorage(context)
      .get(request.getString("actualCostRecordId"))
      .put("status", CANCELLED.getValue())
      .put("additionalInfoForStaff", request.getString("additionalInfoForStaff"));

    created(response).writeTo(context.response());
  }

  private Map<String, JsonObject> getAccountsStorage(RoutingContext context) {
    return getStorage().getTenantResources("/accounts", getTenant(context));
  }

  private Map<String, JsonObject> getActualCostRecordStorage(RoutingContext context) {
    return getStorage()
      .getTenantResources("/actual-cost-record-storage/actual-cost-records", getTenant(context));
  }

  private JsonObject getAccountById(RoutingContext context) {
    final String accountId = context.pathParam("accountId");
    return getAccountsStorage(context).get(accountId);
  }

  private void updateAccount(RoutingContext context, JsonObject updatedAccount) {
    final String accountId = updatedAccount.getString("id");
    getAccountsStorage(context).put(accountId, updatedAccount);
  }

  private JsonObject createFeeFineAction(RoutingContext context, JsonObject account, String actionType,
    double actionAmount, double balance) {

    final String feeFineActionId = UUID.randomUUID().toString();

    final JsonObject feeFineAction = new JsonObject()
      .put("dateAction", formatDateTime(ClockUtil.getZonedDateTime()))
      .put("typeAction", actionType)
      .put("notify", false)
      .put("amountAction", actionAmount)
      .put("balance", balance)
      .put("createdAt", "Circ Desk 1")
      .put("source", "Folio, Tester")
      .put("accountId", account.getString("id"))
      .put("userId", account.getString("userId"))
      .put("id", feeFineActionId);

    final String tenant = getTenant(context);
    getStorage().getTenantResources("/feefineactions", tenant)
      .put(feeFineActionId, feeFineAction);

    return feeFineAction;
  }

  private Handler<RoutingContext> validateRequest(JsonSchemaValidator schemaValidator) {
    return context -> {
      final Result<String> validateResult = schemaValidator.validate(context
        .getBodyAsString());

      if (validateResult.failed()) {
        validateResult.cause().writeTo(context.response());
      } else {
        context.next();
      }
    };
  }

  private static String getTenant(RoutingContext context) {
    return context.request().headers().get(OKAPI_TENANT_HEADER);
  }
}
