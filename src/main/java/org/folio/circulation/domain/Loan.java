package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.Item;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import static org.folio.circulation.domain.representations.LoanProperties.DUE_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.STATUS;
import static org.folio.circulation.support.HttpResult.failure;
import static org.folio.circulation.support.JsonPropertyFetcher.*;
import static org.folio.circulation.support.JsonPropertyWriter.write;

public class Loan implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject representation;
  private Item item;
  private User user;

  public Loan(JsonObject representation) {
    this.representation = representation;
  }

  public static Loan from(JsonObject representation) {
    return new Loan(representation);
  }

  public static Loan from(JsonObject representation, Item item) {
    return from(representation, item, null);
  }

  public static Loan from(JsonObject representation, Item item, User user) {
    final Loan loan = new Loan(representation);

    loan.setItem(item);
    loan.setUser(user);

    return loan;
  }

  JsonObject asJson() {
    return representation.copy();
  }

  public void changeDueDate(DateTime dueDate) {
    write(representation, DUE_DATE, dueDate);
  }

  private void changeAction(String action) {
    representation.put("action", action);
  }

  public void changeProxyUser(String userId) {
    representation.put("proxyUserId", userId);
  }

  public HttpResult<Void> isValidStatus() {
    if(!representation.containsKey(STATUS)) {
      return failure(new ServerErrorFailure(
        "Loan does not have a status"));
    }

    switch(getStatus()) {
      case "Open":
      case "Closed":
        return HttpResult.success(null);

      default:
        return failure(ValidationErrorFailure.failure(
          "Loan status must be \"Open\" or \"Closed\"", STATUS, getStatus()));
    }
  }

  boolean isClosed() {
    return StringUtils.equals(getStatus(), "Closed");
  }

  private String getStatus() {
    return getNestedStringProperty(representation, STATUS, "name");
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  @Override
  public String getItemId() {
    return getProperty(representation, "itemId");
  }

  public DateTime getLoanDate() {
    return getDateTimeProperty(representation, "loanDate");
  }

  @Override
  public String getUserId() {
    return representation.getString("userId");
  }

  @Override
  public String getProxyUserId() {
    return representation.getString("proxyUserId");
  }

  public Item getItem() {
    return item;
  }

  private void setItem(Item newItem) {
    //TODO: Refuse if ID does not match property in representation
    if(newItem != null) {
      representation.put("itemId", newItem.getItemId());
    }
    this.item = newItem;
  }

  public User getUser() {
    return user;
  }

  private void setUser(User newUser) {
    //TODO: Refuse if ID does not match property in representation
    if(newUser != null) {
      representation.put("userId", newUser.getId());
    }
    this.user = newUser;
  }

  private void changeLoanPolicy(String newLoanPolicyId) {
    if(newLoanPolicyId != null) {
      representation.put("loanPolicyId", newLoanPolicyId);
    }
  }

  public Loan renew(DateTime dueDate, String basedUponLoanPolicyId) {
    changeAction("renewed");
    changeLoanPolicy(basedUponLoanPolicyId);
    changeDueDate(dueDate);
    incrementRenewalCount();

    return this;
  }

  private void incrementRenewalCount() {
    write(representation, "renewalCount", getRenewalCount() + 1);
  }

  public Integer getRenewalCount() {
    return getIntegerProperty(representation, "renewalCount", 0);
  }

  public DateTime getDueDate() {
    return getDateTimeProperty(representation, DUE_DATE);
  }
}
