package org.folio.circulation.domain;

import static java.lang.String.format;
import static org.folio.circulation.domain.FeeAmount.noFeeAmount;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.utils.DateTimeUtil.compareToMillis;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.folio.circulation.support.json.JsonPropertyWriter;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class Account {
  private final String id;
  private final AccountRelatedRecordsInfo relatedRecordsInfo;
  private final FeeAmount amount;
  private final FeeAmount remaining;
  private final String status;
  private final String paymentStatus;
  @Getter private final Collection<FeeFineAction> feeFineActions;
  private final ZonedDateTime creationDate;
  private final ZonedDateTime actualRecordCreationDate;

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
          getProperty(representation, "userId"),
          getDateTimeProperty(representation, "dueDate")),
        new AccountItemInfo(
          getProperty(representation, "itemId"),
          getProperty(representation, "title"),
          getProperty(representation, "barcode"),
          getProperty(representation, "callNumber"),
          getProperty(representation, "location"),
          getProperty(representation, "materialTypeId"),
          getProperty(representation, "materialType"))
      ),
      FeeAmount.from(representation, "amount"),
      FeeAmount.from(representation, "remaining"),
      getNestedStringProperty(representation, "status", "name"),
      getNestedStringProperty(representation, "paymentStatus", "name"),
      Collections.emptyList(),
      getNestedDateTimeProperty(representation, "metadata", "createdDate"),
      null);
  }

  public JsonObject toJson() {
    JsonObject jsonObject = new JsonObject();

    jsonObject.put("id", id);
    jsonObject.put("ownerId", relatedRecordsInfo.getFeeFineOwnerInfo().getOwnerId());
    jsonObject.put("feeFineId", relatedRecordsInfo.getFeeFineTypeInfo().getFeeFineId());
    jsonObject.put("amount", amount.toDouble());
    jsonObject.put("remaining", remaining.toDouble());
    jsonObject.put("feeFineType", relatedRecordsInfo.getFeeFineTypeInfo().getFeeFineType());
    jsonObject.put("feeFineOwner", relatedRecordsInfo.getFeeFineOwnerInfo().getOwner());
    jsonObject.put("title", relatedRecordsInfo.getItemInfo().getTitle());
    jsonObject.put("barcode", relatedRecordsInfo.getItemInfo().getBarcode());
    jsonObject.put("callNumber", relatedRecordsInfo.getItemInfo().getCallNumber());
    jsonObject.put("location", relatedRecordsInfo.getItemInfo().getLocation());
    jsonObject.put("materialTypeId", relatedRecordsInfo.getItemInfo().getMaterialTypeId());
    jsonObject.put("materialType", relatedRecordsInfo.getItemInfo().getMaterialType());
    jsonObject.put("loanId", relatedRecordsInfo.getLoanInfo().getLoanId());
    jsonObject.put("dueDate", relatedRecordsInfo.getLoanInfo().getDueDate().toString());
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

  public FeeAmount getAmount() {
    return amount;
  }

  public FeeAmount getRemaining() {
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

  public String getMaterialType() {
    return relatedRecordsInfo.getItemInfo().getMaterialType();
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

  public Optional<ZonedDateTime> getClosedDate() {
    return feeFineActions.stream()
      .filter(ffa -> ffa.getBalance().hasZeroAmount())
      .max(this::compareByDateAction)
      .map(FeeFineAction::getDateAction);
  }

  public ZonedDateTime getCreationDate() {
    return creationDate;
  }

  public Account withFeeFineActions(Collection<FeeFineAction> actions) {
    return new Account(id, relatedRecordsInfo, amount, remaining, status, paymentStatus, actions,
      creationDate, actualRecordCreationDate);
  }

  public Account withActualRecordCreationDate(ZonedDateTime actualRecordCreationDate) {
    return new Account(id, relatedRecordsInfo, amount, remaining, status, paymentStatus, feeFineActions,
      creationDate, actualRecordCreationDate);
  }

  public ZonedDateTime getActualRecordCreationDate() {
    return actualRecordCreationDate;
  }

  public boolean isClosed() {
    return getStatus().equalsIgnoreCase("closed");
  }

  public boolean isOpen() {
    return getStatus().equalsIgnoreCase("open");
  }

  public FeeAmount getPaidAndTransferredAmount() {
    return feeFineActions.stream()
      .filter(action -> action.isPaid() || action.isTransferred())
      .map(FeeFineAction::getAmount)
      .reduce(FeeAmount::add)
      .orElse(noFeeAmount());
  }

  public boolean hasPaidOrTransferredAmount() {
    return getPaidAndTransferredAmount().hasAmount();
  }

  private int compareByDateAction(FeeFineAction left, FeeFineAction right) {
    return compareToMillis(left.getDateAction(), right.getDateAction());
  }

  @Override
  public String toString() {
    return format("Account(id=%s, feeFineType=%s, amount=%s)", id, getFeeFineType(),
      amount.toScaledString());
  }
}
