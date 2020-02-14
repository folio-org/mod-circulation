package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.math.NumberUtils;
import org.folio.circulation.support.JsonPropertyWriter;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class Account {
  private final String id;
  private final String ownerId;
  private final String feeFineId;
  private final Double amount;
  private final Double remaining;
  private final String feeFineType;
  private final String feeFineOwner;
  private final String title;
  private final String barcode;
  private final String callNumber;
  private final String location;
  private final String materialTypeId;
  private final String loanId;
  private final String userId;
  private final String itemId;
  private final String status;
  private final String paymentStatus;

  private Collection<FeeFineAction> feeFineActions = new ArrayList<>();

  public Account(Loan loan, Item item, FeeFineOwner feeFineOwner,
    FeeFine feeFine, Double amount) {
    this.id = UUID.randomUUID().toString();
    this.ownerId = feeFineOwner.getId();
    this.feeFineId = feeFine.getId();
    this.amount = amount;
    this.remaining = amount;
    this.feeFineType = feeFine.getFeeFineType();
    this.feeFineOwner = feeFineOwner.getOwner();
    this.title = item.getTitle();
    this.barcode = item.getBarcode();
    this.callNumber = item.getCallNumber();
    this.location = item.getLocation().getPrimaryServicePointId().toString();
    this.materialTypeId = item.getMaterialTypeId();
    this.loanId = loan.getId();
    this.userId = loan.getUserId();
    this.itemId = item.getItemId();
    this.status = "Open";
    this.paymentStatus = "Outstanding";
  }

  public Account(String id, String ownerId, String feeFineId, Double amount, Double remaining,
    String feeFineType, String feeFineOwner, String title, String barcode, String callNumber,
    String location, String materialTypeId, String loanId, String userId, String itemId,
    String status, String paymentStatus) {
    this.id = id;
    this.ownerId = ownerId;
    this.feeFineId = feeFineId;
    this.amount = amount;
    this.remaining = remaining;
    this.feeFineType = feeFineType;
    this.feeFineOwner = feeFineOwner;
    this.title = title;
    this.barcode = barcode;
    this.callNumber = callNumber;
    this.location = location;
    this.materialTypeId = materialTypeId;
    this.loanId = loanId;
    this.userId = userId;
    this.itemId = itemId;
    this.status = status;
    this.paymentStatus = paymentStatus;
  }

  public static Account from(JsonObject representation) {
    return new Account(getProperty(representation, "id"),
      getProperty(representation, "ownerId"),
      getProperty(representation, "feeFineId"),
      representation != null ? representation.getDouble("amount") : null,
      representation != null ? representation.getDouble("remaining") : null,
      getProperty(representation, "feeFineType"),
      getProperty(representation, "feeFineOwner"),
      getProperty(representation, "title"),
      getProperty(representation, "barcode"),
      getProperty(representation, "callNumber"),
      getProperty(representation, "location"),
      getProperty(representation, "materialTypeId"),
      getProperty(representation, "loanId"),
      getProperty(representation, "userId"),
      getProperty(representation, "itemId"),
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

    jsonObject.put("id", this.id);
    jsonObject.put("ownerId", this.ownerId);
    jsonObject.put("feeFineId", this.feeFineId);
    jsonObject.put("amount", this.amount);
    jsonObject.put("remaining", this.remaining);
    jsonObject.put("feeFineType", this.feeFineType);
    jsonObject.put("feeFineOwner", this.feeFineOwner);
    jsonObject.put("title", this.title);
    jsonObject.put("barcode", this.barcode);
    jsonObject.put("callNumber", this.callNumber);
    jsonObject.put("location", this.location);
    jsonObject.put("materialTypeId", this.materialTypeId);
    jsonObject.put("loanId", this.loanId);
    jsonObject.put("userId", this.userId);
    jsonObject.put("itemId", this.itemId);

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

  public String getOwnerId() {
    return ownerId;
  }

  public String getFeeFineId() {
    return feeFineId;
  }

  public Double getAmount() {
    return amount;
  }

  public Double getRemaining() {
    return remaining;
  }

  public String getFeeFineType() {
    return feeFineType;
  }

  public String getFeeFineOwner() {
    return feeFineOwner;
  }

  public String getTitle() {
    return title;
  }

  public String getBarcode() {
    return barcode;
  }

  public String getCallNumber() {
    return callNumber;
  }

  public String getLocation() {
    return location;
  }

  public String getMaterialTypeId() {
    return materialTypeId;
  }

  public String getLoanId() {
    return loanId;
  }

  public String getUserId() {
    return userId;
  }

  public String getItemId() {
    return itemId;
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
