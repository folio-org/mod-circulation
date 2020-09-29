package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.HOLDINGS_RECORD_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.INSTANCE_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.LOAN_ID;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;

@AllArgsConstructor
@NoArgsConstructor
@With
public class NoticeLogContextItem {
  private String itemBarcode;
  private String itemId;
  private String instanceId;
  private String holdingsRecordId;
  private String loanId;

  public static NoticeLogContextItem from(Loan loan) {
    Item item = loan.getItem();
    return new NoticeLogContextItem()
        .withItemId(item.getItemId())
        .withItemBarcode(item.getBarcode())
        .withInstanceId(item.getInstanceId())
        .withHoldingsRecordId(item.getHoldingsRecordId())
        .withLoanId(loan.getId());
  }

  public static NoticeLogContextItem from(Request request) {
    Item item = request.getItem();
    return new NoticeLogContextItem()
      .withItemId(item.getItemId())
      .withItemBarcode(item.getBarcode())
      .withInstanceId(item.getInstanceId())
      .withHoldingsRecordId(item.getHoldingsRecordId());
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
    ofNullable(loanId).ifPresent(s -> write(json, LOAN_ID.value(), loanId));
    return json;
  }
}
