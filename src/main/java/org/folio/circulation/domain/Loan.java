package org.folio.circulation.domain;

import static org.folio.circulation.domain.representations.LoanProperties.ACTION_COMMENT;
import static org.folio.circulation.domain.representations.LoanProperties.CHECKIN_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.LoanProperties.DUE_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.RETURN_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.STATUS;
import static org.folio.circulation.domain.representations.LoanProperties.SYSTEM_RETURN_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.USER_ID;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class Loan implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject representation;
  private final Item item;
  private final User user;
  private final User proxy;
  private final DateTime oldDueDate;

  private String checkoutServicePointId;
  private String checkinServicePointId;

  private ServicePoint checkoutServicePoint;
  private ServicePoint checkinServicePoint;

  public Loan(JsonObject representation) {
    this(representation, null, null, null, null, null, null);
  }

  public Loan(JsonObject representation, Item item, User user, User proxy,
              ServicePoint checkinServicePoint, ServicePoint checkoutServicePoint, DateTime oldDueDate) {

    this.representation = representation;
    this.item = item;
    this.user = user;
    this.proxy = proxy;
    this.checkinServicePoint = checkinServicePoint;
    this.checkoutServicePoint = checkoutServicePoint;

    this.checkoutServicePointId = getProperty(representation, LoanProperties.CHECKOUT_SERVICE_POINT_ID);
    this.checkinServicePointId = getProperty(representation, CHECKIN_SERVICE_POINT_ID);

    this.oldDueDate = oldDueDate == null ? getDueDate() : oldDueDate;

    // TODO: Refuse if ID does not match property in representation,
    // and possibly convert isFound to unknown item class
    if (item != null && item.isFound()) {
      representation.put("itemId", item.getItemId());
    }

    // TODO: Refuse if ID does not match property in representation
    if (user != null) {
      representation.put("userId", user.getId());
    }

    // TODO: Refuse if ID does not match property in representation
    if (proxy != null) {
      representation.put("proxyUserId", proxy.getId());
    }

  }

  public static Loan from(JsonObject representation) {
    return from(representation, null);
  }

  public static Loan from(JsonObject representation, Item item) {
    return from(representation, item, null, null);
  }

  public static Loan from(JsonObject representation, Item item, User user, User proxy) {
    return from(representation, item, user, proxy, null);
  }

  public static Loan from(JsonObject representation, Item item, User user, User proxy, DateTime oldDueDate) {
    defaultStatusAndAction(representation);
    return new Loan(representation, item, user, proxy, null, null, oldDueDate);
  }

  JsonObject asJson() {
    return representation.copy();
  }

  public void changeDueDate(DateTime newDueDate) {
    write(representation, DUE_DATE, newDueDate);
  }

  private void changeReturnDate(DateTime returnDate) {
    write(representation, RETURN_DATE, returnDate);
  }

  private void changeSystemReturnDate(DateTime systemReturnDate) {
    write(representation, SYSTEM_RETURN_DATE, systemReturnDate);
  }

  public void changeAction(String action) {
    representation.put(LoanProperties.ACTION, action);
  }

  private void changeCheckInServicePointId(UUID servicePointId) {
    write(representation, "checkinServicePointId", servicePointId);
  }

  private void changeStatus(String status) {
    representation.put(STATUS, new JsonObject().put("name", status));
  }

  public void changeActionComment(String comment) {
    representation.put(ACTION_COMMENT, comment);
  }

  private void removeActionComment() {
    representation.remove(ACTION_COMMENT);
  }

  public Result<Void> isValidStatus() {
    if (!representation.containsKey(STATUS)) {
      return failed(new ServerErrorFailure("Loan does not have a status"));
    }

    switch (getStatus()) {
    case "Open":
    case "Closed":
      return succeeded(null);

    default:
      return failedValidation("Loan status must be \"Open\" or \"Closed\"",
        STATUS, getStatus());
    }
  }

  public Result<Void> openLoanHasUserId() {
    if (Objects.equals(getStatus(), "Open") && getUserId() == null) {
      return failedValidation("Open loan must have a user ID",
        USER_ID, getUserId());
    } else {
      return succeeded(null);
    }
  }

  public Result<Void> closedLoanHasCheckInServicePointId() {
    if (isClosed() && getCheckInServicePointId() == null) {
      return failedValidation("A Closed loan must have a Checkin Service Point",
          CHECKIN_SERVICE_POINT_ID, getCheckInServicePointId());
    } else {
      return succeeded(null);
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
    return new Loan(representation, item, user, proxy, checkinServicePoint,
        checkoutServicePoint, oldDueDate);
  }

  public User getUser() {
    return user;
  }

  public Loan withUser(User newUser) {
    return new Loan(representation, item, newUser, proxy, checkinServicePoint,
        checkoutServicePoint, oldDueDate);
  }

  public User getProxy() {
    return proxy;
  }

  Loan withProxy(User newProxy) {
    return new Loan(representation, item, user, newProxy, checkinServicePoint,
      checkoutServicePoint, oldDueDate);
  }
  
  public Loan withCheckinServicePoint(ServicePoint newCheckinServicePoint) {
    return new Loan(representation, item, user, proxy, newCheckinServicePoint,
      checkoutServicePoint, oldDueDate);
  }
  
  public Loan withCheckoutServicePoint(ServicePoint newCheckoutServicePoint) {
    return new Loan(representation, item, user, proxy, checkinServicePoint,
      newCheckoutServicePoint, oldDueDate);
  }

  private void changeLoanPolicy(String newLoanPolicyId) {
    if (newLoanPolicyId != null) {
      representation.put("loanPolicyId", newLoanPolicyId);
    }
  }
  
  public ServicePoint getCheckinServicePoint() {
    return this.checkinServicePoint;
  }
  
  public ServicePoint getCheckoutServicePoint() {
    return this.checkoutServicePoint;
  }

  public Loan renew(DateTime dueDate, String basedUponLoanPolicyId) {
    changeAction("renewed");
    removeActionComment();
    changeLoanPolicy(basedUponLoanPolicyId);
    changeDueDate(dueDate);
    incrementRenewalCount();

    return this;
  }

  public Loan overrideRenewal(DateTime dueDate,
                              String basedUponLoanPolicyId,
                              String actionComment) {
    changeAction("renewedThroughOverride");
    changeLoanPolicy(basedUponLoanPolicyId);
    changeDueDate(dueDate);
    incrementRenewalCount();
    changeActionComment(actionComment);

    return this;
  }

  Loan checkIn(DateTime returnDate, UUID servicePointId) {
    changeAction("checkedin");
    removeActionComment();
    changeStatus("Closed");
    changeReturnDate(returnDate);
    changeSystemReturnDate(DateTime.now(DateTimeZone.UTC));
    changeCheckInServicePointId(servicePointId);

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
    if (!loan.containsKey(STATUS)) {
      loan.put(STATUS, new JsonObject().put("name", "Open"));

      if (!loan.containsKey(LoanProperties.ACTION)) {
        loan.put(LoanProperties.ACTION, "checkedout");
      }
    }
  }

  public String getCheckoutServicePointId() {
    return checkoutServicePointId;
  }

  public String getCheckInServicePointId() {
    return checkinServicePointId;
  }

  public boolean hasDueDateChanged() {
    return !Objects.equals(oldDueDate, getDueDate());
  }

  public DateTime getOldDueDate() {
    return oldDueDate;
  }
}
