package org.folio.circulation.domain.representations.logs;

import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.HOLDINGS_RECORD_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.INSTANCE_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.LOAN_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.NOTICE_POLICY_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.TEMPLATE_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.TRIGGERING_EVENT;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.notice.session.PatronSessionRecord;

import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@With
public class NoticeLogContextItem {
  private String itemBarcode;
  private String itemId;
  private String instanceId;
  private String holdingsRecordId;
  private String loanId;
  private String servicePointId;
  private String templateId;
  private String noticePolicyId;
  private String triggeringEvent;

  public static NoticeLogContextItem from(Loan loan) {
    NoticeLogContextItem noticeLogContextItem = new NoticeLogContextItem();

    if (loan != null) {
      noticeLogContextItem = noticeLogContextItem
        .withItemId(loan.getItemId())
        .withLoanId(loan.getId())
        .withServicePointId(loan.getCheckoutServicePointId());

      Item item = loan.getItem();

      if (item != null) {
        noticeLogContextItem = noticeLogContextItem
          .withItemBarcode(item.getBarcode())
          .withInstanceId(item.getInstanceId())
          .withHoldingsRecordId(item.getHoldingsRecordId());
      }
    }

    return noticeLogContextItem;
  }

  public static NoticeLogContextItem from(PatronSessionRecord patronSession) {
    return from(patronSession.getLoan())
      .withLoanId(patronSession.getLoanId().toString())
      .withTriggeringEvent(patronSession.getActionType().getRepresentation());
  }

  public static NoticeLogContextItem from(Request request) {
    Item item = request.getItem();
    NoticeLogContextItem logContextItem = new NoticeLogContextItem();
    if (item != null) {
      logContextItem.withItemId(item.getItemId())
        .withItemBarcode(item.getBarcode())
        .withInstanceId(item.getInstanceId())
        .withHoldingsRecordId(item.getHoldingsRecordId());
    }

    if (Objects.nonNull(request.getPickupServicePointId())) {
      return logContextItem.withServicePointId(request.getPickupServicePointId());
    }
    return logContextItem;
  }

  public static NoticeLogContextItem from(Item item) {
    return new NoticeLogContextItem()
      .withItemId(item.getItemId())
      .withItemBarcode(item.getBarcode())
      .withInstanceId(item.getInstanceId())
      .withHoldingsRecordId(item.getHoldingsRecordId());
  }

  public JsonObject asJson() {
    JsonObject json = new JsonObject();
    write(json, ITEM_BARCODE.value(), itemBarcode);
    write(json, ITEM_ID.value(), itemId);
    write(json, INSTANCE_ID.value(), instanceId);
    write(json, HOLDINGS_RECORD_ID.value(), holdingsRecordId);
    write(json, LOAN_ID.value(), loanId);
    write(json, SERVICE_POINT_ID.value(), servicePointId);
    write(json, TEMPLATE_ID.value(), templateId);
    write(json, NOTICE_POLICY_ID.value(), noticePolicyId);
    write(json, TRIGGERING_EVENT.value(), triggeringEvent);
    return json;
  }
}
