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
  private final AccountFeeFineOwnerAndTypeInfo feeFineOwnerAndTypeInfo;
  private final AccountLoanAndItemInfo loanAndItemInfo;
  private final Double amount;
  private final Double remaining;
  private final String status;
  private final String paymentStatus;

  private Collection<FeeFineAction> feeFineActions = new ArrayList<>();

  public Account(String id, AccountFeeFineOwnerAndTypeInfo feeFineOwnerAndTypeInfo,
    AccountLoanAndItemInfo loanAndItemInfo, Double amount, Double remaining,
    String status, String paymentStatus) {
    this.id = id;
    this.feeFineOwnerAndTypeInfo = feeFineOwnerAndTypeInfo;
    this.loanAndItemInfo = loanAndItemInfo;
    this.amount = amount;
    this.remaining = remaining;
    this.status = status;
    this.paymentStatus = paymentStatus;
  }

  public static Account from(JsonObject representation) {
    return new Account(getProperty(representation, "id"),
      new AccountFeeFineOwnerAndTypeInfo(
        getProperty(representation, "ownerId"),
        getProperty(representation, "feeFineOwner"),
        getProperty(representation, "feeFineId"),
        getProperty(representation, "feeFineType")),
      new AccountLoanAndItemInfo(getProperty(representation, "title"),
        getProperty(representation, "barcode"),
        getProperty(representation, "callNumber"),
        getProperty(representation, "location"),
        getProperty(representation, "materialTypeId"),
        getProperty(representation, "loanId"),
        getProperty(representation, "userId"),
        getProperty(representation, "itemId")),
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
    jsonObject.put("ownerId", feeFineOwnerAndTypeInfo.getOwnerId());
    jsonObject.put("feeFineId", feeFineOwnerAndTypeInfo.getFeeFineId());
    jsonObject.put("amount", amount);
    jsonObject.put("remaining", remaining);
    jsonObject.put("feeFineType", feeFineOwnerAndTypeInfo.getFeeFineType());
    jsonObject.put("feeFineOwner", feeFineOwnerAndTypeInfo.getOwner());
    jsonObject.put("title", loanAndItemInfo.getTitle());
    jsonObject.put("barcode", loanAndItemInfo.getBarcode());
    jsonObject.put("callNumber", loanAndItemInfo.getCallNumber());
    jsonObject.put("location", loanAndItemInfo.getLocation());
    jsonObject.put("materialTypeId", loanAndItemInfo.getMaterialTypeId());
    jsonObject.put("loanId", loanAndItemInfo.getLoanId());
    jsonObject.put("userId", loanAndItemInfo.getUserId());
    jsonObject.put("itemId", loanAndItemInfo.getItemId());

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
    return feeFineOwnerAndTypeInfo.getFeeFineType();
  }

  public String getFeeFineOwner() {
    return feeFineOwnerAndTypeInfo.getOwner();
  }

  public String getTitle() {
    return loanAndItemInfo.getTitle();
  }

  public String getBarcode() {
    return loanAndItemInfo.getBarcode();
  }

  public String getCallNumber() {
    return loanAndItemInfo.getCallNumber();
  }

  public String getLocation() {
    return loanAndItemInfo.getLocation();
  }

  public String getMaterialTypeId() {
    return loanAndItemInfo.getMaterialTypeId();
  }

  public String getLoanId() {
    return loanAndItemInfo.getLoanId();
  }

  public String getUserId() {
    return loanAndItemInfo.getUserId();
  }

  public String getItemId() {
    return loanAndItemInfo.getItemId();
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
