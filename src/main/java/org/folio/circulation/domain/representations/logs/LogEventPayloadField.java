package org.folio.circulation.domain.representations.logs;

public enum LogEventPayloadField {
  DATE("date"),
  FEE_FINE_ID("feeFineId"),
  HOLDINGS_RECORD_ID("holdingsRecordId"),
  INSTANCE_ID("instanceId"),
  ITEM_BARCODE("itemBarcode"),
  ITEM_ID("itemId"),
  ITEMS("items"),
  LOAN_ID("loanId"),
  LOG_EVENT_TYPE("logEventType"),
  NOTICE_POLICY_ID("noticePolicyId"),
  PAYLOAD("payload"),
  REQUEST_ID("requestId"),
  SERVICE_POINT_ID("servicePointId"),
  TEMPLATE_ID("templateId"),
  TRIGGERING_EVENT("triggeringEvent"),
  USER_BARCODE("userBarcode"),
  USER_ID("userId");

  private final String value;

  LogEventPayloadField(String value) {
    this.value = value;
  }

  public String value() {
    return this.value;
  }
}
