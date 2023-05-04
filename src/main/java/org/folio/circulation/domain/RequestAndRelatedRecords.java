package org.folio.circulation.domain;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.RequestPolicy;

import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
public class RequestAndRelatedRecords implements UserRelatedRecord, ItemRelatedRecord {
  @ToString.Include
  private final Request request;
  private final Request originalRequest;
  private final RequestQueue requestQueue;
  private final RequestPolicy requestPolicy;
  private final ZoneId timeZone;
  private final MoveRequestRecord moveRequestRecord;
  private final ZonedDateTime recalledLoanPreviousDueDate;

  private RequestAndRelatedRecords(
    Request request,
    RequestQueue requestQueue,
    RequestPolicy requestPolicy,
    MoveRequestRecord moveRequestRecord,
    ZoneId timeZone,
    ZonedDateTime recalledLoanPreviousDueDate) {

    this.request = request;
    this.originalRequest = request.copy();
    this.requestQueue = requestQueue;
    this.requestPolicy = requestPolicy;
    this.timeZone = timeZone;
    this.moveRequestRecord = moveRequestRecord;
    this.recalledLoanPreviousDueDate = recalledLoanPreviousDueDate;
  }

  public RequestAndRelatedRecords(Request request) {
    this(request, null, null, null, ZoneOffset.UTC, null);
  }

  public boolean isTlrFeatureEnabled() {
    TlrSettingsConfiguration tlrSettingsConfiguration = request.getTlrSettingsConfiguration();
    return tlrSettingsConfiguration != null &&
      tlrSettingsConfiguration.isTitleLevelRequestsFeatureEnabled();
  }

  public RequestAndRelatedRecords withRequest(Request newRequest) {
    Item item = this.request.getItem();
    return new RequestAndRelatedRecords(
      newRequest.withItem(item == null ? newRequest.getItem() : item),
      this.requestQueue,
      null,
      this.moveRequestRecord,
      this.timeZone,
      this.recalledLoanPreviousDueDate
    );
  }

  public RequestAndRelatedRecords withRequestPolicy(RequestPolicy newRequestPolicy) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      newRequestPolicy,
      this.moveRequestRecord,
      this.timeZone,
      this.recalledLoanPreviousDueDate
    );
  }

  public RequestAndRelatedRecords withRecalledLoanPreviousDueDate(ZonedDateTime recalledLoanPreviousDueDate) {
    return new RequestAndRelatedRecords(
      this.request,
      requestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone,
      recalledLoanPreviousDueDate
    );
  }

  public RequestAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new RequestAndRelatedRecords(
      this.request,
      newRequestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone,
      this.recalledLoanPreviousDueDate
    );
  }

  public RequestAndRelatedRecords withItem(Item newItem) {
    return new RequestAndRelatedRecords(
      this.request.withItem(newItem),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone,
      this.recalledLoanPreviousDueDate
    );
  }

  public RequestAndRelatedRecords withLoan(Loan newLoan) {
    return new RequestAndRelatedRecords(
      this.request.withLoan(newLoan),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone,
      this.recalledLoanPreviousDueDate
    );
  }

  public RequestAndRelatedRecords withRequestType(RequestType newRequestType) {
    return new RequestAndRelatedRecords(
      this.request.withRequestType(newRequestType),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone,
      this.recalledLoanPreviousDueDate
    );
  }

  RequestAndRelatedRecords withTlrSettings(TlrSettingsConfiguration tlrSettings) {
    return new RequestAndRelatedRecords(
      this.request.withTlrSettingsConfiguration(tlrSettings),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone,
      this.recalledLoanPreviousDueDate
    );
  }

  RequestAndRelatedRecords withTimeZone(ZoneId newTimeZone) {
    return new RequestAndRelatedRecords(request, requestQueue, requestPolicy,
      moveRequestRecord, newTimeZone, recalledLoanPreviousDueDate);
  }

  public RequestAndRelatedRecords asMove(String originalItemId, String destinationItemId) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      this.requestPolicy,
      MoveRequestRecord.with(originalItemId, destinationItemId),
      this.timeZone,
      this.recalledLoanPreviousDueDate
    );
  }

  public Request getRequest() {
    return request;
  }

  public RequestQueue getRequestQueue() {
    return requestQueue;
  }

  public RequestPolicy getRequestPolicy() { return requestPolicy; }

  String getSourceItemId() {
    return moveRequestRecord != null ? moveRequestRecord.getSourceItemId() : null;
  }

  String getDestinationItemId() {
    return moveRequestRecord != null ? moveRequestRecord.getDestinationItemId() : null;
  }

  public ZonedDateTime getRecalledLoanPreviousDueDate() {
    return recalledLoanPreviousDueDate;
  }

  @Override
  public String getUserId() {
    return request.getUserId();
  }

  @Override
  public String getProxyUserId() {
    return request.getProxyUserId();
  }

  @Override
  public User getUser() {
    return request.getUser();
  }

  @Override
  public String getItemId() {
    return request.getItemId();
  }

  @Override
  public Item getItem() {
    return request.getItem();
  }

  ZoneId getTimeZone() {
    return timeZone;
  }

  public Request getOriginalRequest() {
    return originalRequest;
  }
}
