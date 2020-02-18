package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import org.apache.commons.lang3.math.NumberUtils;
import org.folio.circulation.support.JsonPropertyWriter;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class Account {
  private final String id;
  private final AccountRelatedRecordsInfo relatedRecordsInfo;
  private final Double amount;
  private final Double remaining;
  private final String status;
  private final String paymentStatus;

  private Collection<FeeFineAction> feeFineActions = new ArrayList<>();

  public Account(String id, AccountRelatedRecordsInfo relatedRecordsInfo, Double amount,
    Double remaining, String status, String paymentStatus) {
    this.id = id;
    this.relatedRecordsInfo = relatedRecordsInfo;
    this.amount = amount;
    this.remaining = remaining;
    this.status = status;
    this.paymentStatus = paymentStatus;
  }

  public static Account from(JsonObject representation) {
    return new Account(getProperty(representation, "id"),
      new AccountRelatedRecordsInfo(
        new AccountFeeFineOwnerInfo(
          getProperty(representation, "ownerId"),
          getProperty(representation, "feeFineOwner")),
        new AccountFeeFineTypeInfo(
          getProperty(representation, "feeFineId"),
          getProperty(representation, "feeFineType")),
        new AccountLoanInfo(
          getProperty(representation, "loanId"),
          getProperty(representation, "userId")),
        new AccountItemInfo(
          getProperty(representation, "itemId"),
          getProperty(representation, "title"),
          getProperty(representation, "barcode"),
          getProperty(representation, "callNumber"),
          getProperty(representation, "location"),
          getProperty(representation, "materialTypeId"))
      ),
      representation != null ? representation.getDouble("amount") : null,
      representation != null ? representation.getDouble("remaining") : null,
      getNestedStringProperty(representation, "status", "name"),
      getNestedStringProperty(representation, "paymentStatus", "name"));
  }

  private static Account from(JsonObject representation, Collection<FeeFineAction> actions) {
    Account account = Account.from(representation);
    account.setFeeFineActions(actions == null ? new ArrayList<>() : actions);
    return account;
  }

  public JsonObject toJson() {
    JsonObject jsonObject = new JsonObject();

    jsonObject.put("id", id);
    jsonObject.put("ownerId", relatedRecordsInfo.getFeeFineOwnerInfo().getOwnerId());
    jsonObject.put("feeFineId", relatedRecordsInfo.getFeeFineTypeInfo().getFeeFineId());
    jsonObject.put("amount", amount);
    jsonObject.put("remaining", remaining);
    jsonObject.put("feeFineType", relatedRecordsInfo.getFeeFineTypeInfo().getFeeFineType());
    jsonObject.put("feeFineOwner", relatedRecordsInfo.getFeeFineOwnerInfo().getOwner());
    jsonObject.put("title", relatedRecordsInfo.getItemInfo().getTitle());
    jsonObject.put("barcode", relatedRecordsInfo.getItemInfo().getBarcode());
    jsonObject.put("callNumber", relatedRecordsInfo.getItemInfo().getCallNumber());
    jsonObject.put("location", relatedRecordsInfo.getItemInfo().getLocation());
    jsonObject.put("materialTypeId", relatedRecordsInfo.getItemInfo().getMaterialTypeId());
    jsonObject.put("loanId", relatedRecordsInfo.getLoanInfo().getLoanId());
    jsonObject.put("userId", relatedRecordsInfo.getLoanInfo().getUserId());
    jsonObject.put("itemId", relatedRecordsInfo.getItemInfo().getItemId());

    JsonObject paymentStatusJsonObject = new JsonObject();
    JsonPropertyWriter.write(paymentStatusJsonObject, "name", this.paymentStatus);
    jsonObject.put("paymentStatus", paymentStatusJsonObject);

    JsonObject statusJsonObject = new JsonObject();
    JsonPropertyWriter.write(statusJsonObject, "name", this.status);
    jsonObject.put("status", statusJsonObject);

    return jsonObject;
  }

  public String getId() {
    return id;
  }

  public Double getAmount() {
    return amount;
  }

  public Double getRemaining() {
    return remaining;
  }

  public String getFeeFineType() {
    return relatedRecordsInfo.getFeeFineTypeInfo().getFeeFineType();
  }

  public String getFeeFineOwner() {
    return relatedRecordsInfo.getFeeFineOwnerInfo().getOwner();
  }

  public String getTitle() {
    return relatedRecordsInfo.getItemInfo().getTitle();
  }

  public String getBarcode() {
    return relatedRecordsInfo.getItemInfo().getBarcode();
  }

  public String getCallNumber() {
    return relatedRecordsInfo.getItemInfo().getCallNumber();
  }

  public String getLocation() {
    return relatedRecordsInfo.getItemInfo().getLocation();
  }

  public String getMaterialTypeId() {
    return relatedRecordsInfo.getItemInfo().getMaterialTypeId();
  }

  public String getLoanId() {
    return relatedRecordsInfo.getLoanInfo().getLoanId();
  }

  public String getUserId() {
    return relatedRecordsInfo.getLoanInfo().getUserId();
  }

  public String getItemId() {
    return relatedRecordsInfo.getItemInfo().getItemId();
  }

  public String getStatus() {
    return status;
  }

  public String getPaymentStatus() {
    return paymentStatus;
  }

  public Double getRemainingFeeFineAmount() {
    return this.remaining;
  }

  public void setFeeFineActions(Collection<FeeFineAction> feeFineActions) {
    this.feeFineActions = feeFineActions;
  }

  public Optional<DateTime> getClosedDate() {
    return feeFineActions.stream()
      .filter(ffa -> ffa.getBalance().equals(NumberUtils.DOUBLE_ZERO))
      .max(Comparator.comparing(FeeFineAction::getDateAction))
      .map(FeeFineAction::getDateAction);
  }

  public Account withFeeFineActions(Collection<FeeFineAction> actions) {
    return Account.from(toJson(), actions);
  }

  public boolean isClosed() {
    return getStatus().equalsIgnoreCase("closed");
  }

  public boolean isOpen() {
    return getStatus().equalsIgnoreCase("open");
  }
}
