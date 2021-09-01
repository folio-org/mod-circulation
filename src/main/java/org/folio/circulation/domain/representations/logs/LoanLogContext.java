package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.*;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

@AllArgsConstructor
@NoArgsConstructor
@With
public class LoanLogContext {
  private String userBarcode;
  private String userId;
  private String itemBarcode;
  private String itemId;
  private String instanceId;
  private String holdingsRecordId;
  private String action;
  private String actionComment;
  private DateTime date;
  private String servicePointId;
  private String updatedByUserId;
  private String description;
  private String loanId;

  public static LoanLogContext from(Loan loan) {
    return new LoanLogContext()
      .withUser(ofNullable(loan.getUser())
        .orElse(userFromRepresentation(loan)))
      .withItem(ofNullable(loan.getItem())
        .orElse(itemFromRepresentation(loan)))
      .withAction(LogContextActionResolver.resolveAction(loan.getAction()))
      .withDate(ClockUtil.getDateTime())
      .withServicePointId(ofNullable(loan.getCheckInServicePointId())
        .orElse(loan.getCheckoutServicePointId()))
      .withLoanId(loan.getId())
      .withActionComment(loan.getActionComment())
      .withUpdatedByUserId(loan.getUpdatedByUserId());
  }

  private LoanLogContext withUser(User user) {
    userBarcode = user.getBarcode();
    userId = user.getId();
    return this;
  }

  private LoanLogContext withItem(Item item) {
    itemBarcode = item.getBarcode();
    itemId = item.getItemId();
    instanceId = item.getInstanceId();
    holdingsRecordId = item.getHoldingsRecordId();
    return this;
  }

  private static User userFromRepresentation(Loan loan) {
    JsonObject userJson = new JsonObject();
    ofNullable(loan).ifPresent(l -> write(userJson, "id", l.getUserId()));
    return new User(userJson);
  }

  private static Item itemFromRepresentation(Loan loan) {
    JsonObject itemJson = new JsonObject();
    ofNullable(loan).ifPresent(l -> write(itemJson, "id", l.getItemId()));
    return Item.from(itemJson);
  }

  public JsonObject asJson() {
    JsonObject json = new JsonObject();
    ofNullable(userBarcode).ifPresent(userBarcode -> write(json, USER_BARCODE.value(), userBarcode));
    ofNullable(userId).ifPresent(userId -> write(json, USER_ID.value(), userId));
    write(json, ITEM_BARCODE.value(), itemBarcode);
    write(json, ITEM_ID.value(), itemId);
    write(json, INSTANCE_ID.value(), instanceId);
    write(json, HOLDINGS_RECORD_ID.value(), holdingsRecordId);
    write(json, ACTION.value(), action);
    ofNullable(actionComment).ifPresent(comment -> write(json, ACTION_COMMENT.value(), actionComment));
    write(json, DATE.value(), date);
    ofNullable(servicePointId).ifPresent(spId -> write(json, SERVICE_POINT_ID.value(), spId));
    ofNullable(updatedByUserId).ifPresent(userId -> write(json, UPDATED_BY_USER_ID.value(), userId));
    write(json, DESCRIPTION.value(), description);
    write(json, LOAN_ID.value(), loanId);
    return json;
  }
}
