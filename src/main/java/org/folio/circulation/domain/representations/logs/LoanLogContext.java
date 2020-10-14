package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ACTION;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ACTION_COMMENT;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.DATE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.DESCRIPTION;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.HOLDINGS_RECORD_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.INSTANCE_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.LOAN_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.UPDATED_BY_USER_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.USER_BARCODE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.USER_ID;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.joda.time.DateTime;

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
  @Setter private String actionComment;
  private DateTime date;
  @Setter private String servicePointId;
  @Setter private String updatedByUserId;
  private String description;
  private String loanId;

  public static LoanLogContext from(Loan loan) {
    LoanLogContext loanLogContext = new LoanLogContext()
      .withUser(loan.getUser())
      .withItem(loan.getItem())
      .withAction(LogContextActionResolver.resolveAction(loan.getAction()))
      .withDate(DateTime.now())
      .withLoanId(loan.getId());
    ofNullable(loan.getActionComment()).ifPresent(loanLogContext::setActionComment);
    ofNullable(loan.getUpdatedByUserId()).ifPresent(loanLogContext::setUpdatedByUserId);
    ofNullable(loan.getCheckoutServicePointId()).ifPresent(loanLogContext::setServicePointId);
    return loanLogContext;
  }

  public LoanLogContext withUser(User user) {
    ofNullable(user).ifPresent(usr -> {
      userBarcode = usr.getBarcode();
      userId = usr.getId();
    });
      return this;
  }

  public LoanLogContext withItem(Item item) {
      itemBarcode = item.getBarcode();
      itemId = item.getItemId();
      instanceId = item.getInstanceId();
      holdingsRecordId = item.getHoldingsRecordId();
      return this;
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
