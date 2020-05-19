package org.folio.circulation.domain;

import static org.folio.circulation.domain.FeeAmount.noFeeAmount;
import static org.folio.circulation.domain.FeeAmount.zeroFeeAmount;
import static org.folio.circulation.domain.representations.AccountStatus.CLOSED;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;

import org.apache.commons.lang3.math.NumberUtils;
import org.folio.circulation.domain.representations.AccountPaymentStatus;
import org.folio.circulation.domain.representations.AccountStatus;
import org.folio.circulation.support.JsonPropertyWriter;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class Account {
  private final String id;
  private final AccountRelatedRecordsInfo relatedRecordsInfo;
  private final FeeAmount amount;
  private final FeeAmount remaining;
  private final String status;
  private final String paymentStatus;
  private final Collection<FeeFineAction> feeFineActions;

  public Account(String id, AccountRelatedRecordsInfo relatedRecordsInfo, FeeAmount amount,
    FeeAmount remaining, String status, String paymentStatus, Collection<FeeFineAction> actions) {

    this.id = id;
    this.relatedRecordsInfo = relatedRecordsInfo;
    this.amount = amount;
    this.remaining = remaining;
    this.status = status;
    this.paymentStatus = paymentStatus;
    this.feeFineActions = actions;
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
      FeeAmount.from(representation, "amount"),
      FeeAmount.from(representation, "remaining"),
      getNestedStringProperty(representation, "status", "name"),
      getNestedStringProperty(representation, "paymentStatus", "name"),
      Collections.emptyList());
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

  public FeeAmount getAmount() {
    return amount;
  }

  public FeeAmount getRemaining() {
    return remaining;
  }

  public boolean hasRemainingAmount() {
    return remaining.hasAmount();
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

  public Optional<DateTime> getClosedDate() {
    return feeFineActions.stream()
      .filter(ffa -> ffa.getBalance().equals(NumberUtils.DOUBLE_ZERO))
      .max(Comparator.comparing(FeeFineAction::getDateAction))
      .map(FeeFineAction::getDateAction);
  }

  public Account withFeeFineActions(Collection<FeeFineAction> actions) {
    return new Account(id, relatedRecordsInfo, amount, remaining, status, paymentStatus, actions);
  }

  public boolean isClosed() {
    return getStatus().equalsIgnoreCase("closed");
  }

  public boolean isOpen() {
    return getStatus().equalsIgnoreCase("open");
  }

  public FeeAmount getPaidAmount() {
    return feeFineActions.stream()
      .filter(FeeFineAction::isPaid)
      .map(FeeFineAction::getAmount)
      .reduce(FeeAmount::add)
      .orElse(noFeeAmount());
  }

  public boolean hasPaidAmount() {
    return getPaidAmount().hasAmount();
  }

  public FeeAmount getTransferredAmount() {
    return feeFineActions.stream()
      .filter(FeeFineAction::isTransferred)
      .map(FeeFineAction::getAmount)
      .reduce(FeeAmount::add)
      .orElse(noFeeAmount());
  }

  public boolean hasTransferredAmount() {
    return getTransferredAmount().hasAmount();
  }

  public String getTransferAccountName() {
    return feeFineActions.stream()
      .filter(FeeFineAction::isTransferred)
      .map(FeeFineAction::getPaymentMethod)
      .findFirst()
      .orElse("");
  }

  public Account subtractRemainingAmount(FeeAmount toSubtract) {
    return new Account(this.id, relatedRecordsInfo, amount, remaining.subtract(toSubtract),
      status, paymentStatus, feeFineActions);
  }

  public Account addRemainingAmount(FeeAmount toAdd) {
    return new Account(this.id, relatedRecordsInfo, amount, remaining.add(toAdd),
      status, paymentStatus, feeFineActions);
  }

  private Account withStatus(AccountStatus status) {
    return new Account(id, relatedRecordsInfo, amount, remaining, status.getValue(),
      paymentStatus, feeFineActions);
  }

  public Account withPaymentStatus(AccountPaymentStatus paymentStatus) {
    return new Account(id, relatedRecordsInfo, amount, remaining, status,
      paymentStatus.getValue(), feeFineActions);
  }

  private Account withRemaining(FeeAmount remaining) {
    return new Account(id, relatedRecordsInfo, amount, remaining, status,
      paymentStatus, feeFineActions);
  }

  public Account close(AccountPaymentStatus paymentAction) {
    return withStatus(CLOSED)
      .withPaymentStatus(paymentAction)
      .withRemaining(zeroFeeAmount());
  }
}
