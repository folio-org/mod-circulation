package org.folio.circulation.domain.representations.logs;

public enum LogEventPayloadField {

  ACCOUNT_ID("accountId"),
  ACTION_COMMENT("actionComment"),
  UPDATED_BY_USER_ID("updatedByUserId"),
  DESCRIPTION("description"),
  ACTION("action"),
  DATE("date"),
  FEE_FINE_ID("feeFineId"),
  HOLDINGS_RECORD_ID("holdingsRecordId"),
  INSTANCE_ID("instanceId"),
  ITEMS("items"),
  NOTICE_POLICY_ID("noticePolicyId"),
  TEMPLATE_ID("templateId"),
  TRIGGERING_EVENT("triggeringEvent"),
  PAYLOAD("payload"),
  REQ_ID("requestId"),
  LOG_EVENT_TYPE("logEventType"),
  CLAIMED_RETURNED_RESOLUTION("claimedReturnedResolution"),
  SERVICE_POINT_ID("servicePointId"),
  REQUESTS("requests"),
  ITEM_ID("itemId"),
  ITEM_BARCODE("itemBarcode"),
  ITEM_STATUS_NAME("itemStatusName"),
  DESTINATION_SERVICE_POINT("destinationServicePoint"),
  SOURCE("source"),
  LOAN_ID("loanId"),
  IS_LOAN_CLOSED("isLoanClosed"),
  SYSTEM_RETURN_DATE("systemReturnDate"),
  RETURN_DATE("returnDate"),
  DUE_DATE("dueDate"),
  ZONE_ID("zoneId"),
  USER_ID("userId"),
  USER_BARCODE("userBarcode"),
  PROXY_BARCODE("proxyBarcode"),
  REQUEST_ID("id"),
  OLD_REQUEST_STATUS("oldRequestStatus"),
  NEW_REQUEST_STATUS("newRequestStatus"),
  PICK_UP_SERVICE_POINT("pickUpServicePoint"),
  REQUEST_TYPE("requestType"),
  REQUEST_ADDRESS_TYPE("addressType"),
  ERROR_MESSAGE("errorMessage");

  private final String value;

  LogEventPayloadField(String value) {
    this.value = value;
  }

  public String value() {
    return this.value;
  }
}
