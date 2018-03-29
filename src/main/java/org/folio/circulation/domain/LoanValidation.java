package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;

public class LoanValidation {
  private LoanValidation() { }

  public static HttpResult<LoanAndRelatedRecords> refuseWhenItemDoesNotExist(
    HttpResult<LoanAndRelatedRecords> result) {

    return result.next(loan -> {
      if(loan.inventoryRecords.getItem() == null) {
        return HttpResult.failure(new ValidationErrorFailure(
          "Item does not exist", "itemId", loan.loan.getString("itemId")));
      }
      else {
        return result;
      }
    });
  }

  public static void defaultStatusAndAction(JsonObject loan) {
    if(!loan.containsKey("status")) {
      loan.put("status", new JsonObject().put("name", "Open"));

      if(!loan.containsKey("action")) {
        loan.put("action", "checkedout");
      }
    }
  }
}
