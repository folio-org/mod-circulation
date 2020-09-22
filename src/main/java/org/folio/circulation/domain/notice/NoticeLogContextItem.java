package org.folio.circulation.domain.notice;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;

import java.util.Objects;

public class NoticeLogContextItem {
  private String id;
  private String barcode;
  private String instanceId;
  private String holdingId;
  private String loanId;
  private String requestId;
  private String feeFineId;

  public static NoticeLogContextItem from(Item item) {
    return new NoticeLogContextItem()
      .withId(item.getItemId())
      .withBarcode(item.getBarcode())
      .withInstanceId(item.getInstanceId())
      .withHoldingId(item.getHoldingsRecordId());
  }

  public static NoticeLogContextItem from(Loan loan) {
    Item item = loan.getItem();
    return from(item).withLoanId(loan.getId());
  }

  public NoticeLogContextItem withId(String id) {
    this.id = id;
    return this;
  }

  public NoticeLogContextItem withBarcode(String barcode) {
    this.barcode = barcode;
    return this;
  }

  public NoticeLogContextItem withInstanceId(String instanceId) {
    this.instanceId = instanceId;
    return this;
  }

  public NoticeLogContextItem withHoldingId(String holdingId) {
    this.holdingId = holdingId;
    return this;
  }

  public NoticeLogContextItem withLoanId(String loanId) {
    this.loanId = loanId;
    return this;
  }

  public NoticeLogContextItem withRequestId(String requestId) {
    this.requestId = requestId;
    return this;
  }

  public void setFeeFineId(String feeFineId) {
    this.feeFineId = feeFineId;
  }

  public JsonObject asJson() {
    JsonObject json = new JsonObject();
    write(json, "itemBarcode", barcode);
    write(json, "itemId", id);
    write(json, "instanceId", instanceId);
    write(json, "holdingId", holdingId);
    if (Objects.nonNull(loanId)) {
      write(json, "loanId", loanId);
    }
    if (Objects.nonNull(requestId)) {
      write(json, "requestId", requestId);
    }
    if (Objects.nonNull(feeFineId)) {
      write(json, "feeFineId", feeFineId);
    }
    return json;
  }
}
