package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.joda.time.DateTime;

import static org.folio.circulation.domain.representations.LoanProperties.DUE_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.STATUS;
import static org.folio.circulation.support.HttpResult.failed;
import static org.folio.circulation.support.JsonPropertyFetcher.*;
import static org.folio.circulation.support.JsonPropertyWriter.write;

public class Loan implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject representation;
  private final Item item;
  private final User user;
  private final User proxy;

  public Loan(JsonObject representation) {
    this(representation, null, null, null);
  }

  public Loan(
    JsonObject representation,
    Item item,
    User user,
    User proxy) {

    this.representation = representation;
    this.item = item;
    this.user = user;
    this.proxy = proxy;

    //TODO: Refuse if ID does not match property in representation,
    // and possibly convert isFound to unknown item class
    if(item != null && item.isFound()) {
      representation.put("itemId", item.getItemId());
    }

    //TODO: Refuse if ID does not match property in representation
    if(user != null) {
      representation.put("userId", user.getId());
    }

    //TODO: Refuse if ID does not match property in representation
    if(proxy != null) {
      representation.put("proxyUserId", proxy.getId());
    }
  }

  public static Loan from(JsonObject representation) {
    return from(representation, null);
  }

  public static Loan from(JsonObject representation, Item item) {
    return from(representation, item, null, null);
  }

  public static Loan from(
    JsonObject representation,
    Item item,
    User user,
    User proxy) {

    defaultStatusAndAction(representation);
    return new Loan(representation, item, user, proxy);
  }

  JsonObject asJson() {
    return representation.copy();
  }

  public void changeDueDate(DateTime dueDate) {
    write(representation, DUE_DATE, dueDate);
  }

  private void changeAction(String action) {
    representation.put(LoanProperties.ACTION, action);
  }

  public HttpResult<Void> isValidStatus() {
    if(!representation.containsKey(STATUS)) {
      return failed(new ServerErrorFailure(
        "Loan does not have a status"));
    }

    switch(getStatus()) {
      case "Open":
      case "Closed":
        return HttpResult.succeeded(null);

      default:
        return failed(ValidationErrorFailure.failure(
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

  public Loan withItem(Item item) {
    return new Loan(representation, item, user, proxy);
  }

  public User getUser() {
    return user;
  }

  Loan withUser(User newUser) {
    return new Loan(representation, item, newUser, proxy);
  }

  public User getProxy() {
    return proxy;
  }

  Loan withProxy(User newProxy) {
    return new Loan(representation, item, user, newProxy);
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

  private static void defaultStatusAndAction(JsonObject loan) {
    if(!loan.containsKey(LoanProperties.STATUS)) {
      loan.put(LoanProperties.STATUS, new JsonObject().put("name", "Open"));

      if(!loan.containsKey(LoanProperties.ACTION)) {
        loan.put(LoanProperties.ACTION, "checkedout");
      }
    }
  }
}
