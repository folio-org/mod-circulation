package org.folio.circulation.domain;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static org.folio.circulation.domain.LoanAction.CHECKED_IN;
import static org.folio.circulation.domain.LoanAction.CHECKED_OUT;
import static org.folio.circulation.domain.LoanAction.DECLARED_LOST;
import static org.folio.circulation.domain.LoanAction.RENEWED;
import static org.folio.circulation.domain.LoanAction.RENEWED_THROUGH_OVERRIDE;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION_COMMENT;
import static org.folio.circulation.domain.representations.LoanProperties.CHECKIN_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.LoanProperties.CHECKOUT_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.LoanProperties.DUE_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.RETURN_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.STATUS;
import static org.folio.circulation.domain.representations.LoanProperties.SYSTEM_RETURN_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.USER_ID;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LostItemPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class Loan implements ItemRelatedRecord, UserRelatedRecord {

  private final JsonObject representation;
  private final Item item;
  private final User user;
  private final User proxy;

  private final Collection<Account> accounts;

  private final DateTime originalDueDate;

  private final String checkoutServicePointId;
  private final String checkinServicePointId;

  private final ServicePoint checkoutServicePoint;
  private final ServicePoint checkinServicePoint;

  private final LoanPolicy loanPolicy;

  private final OverdueFinePolicy overdueFinePolicy;
  private final LostItemPolicy lostItemPolicy;

  private Loan(JsonObject representation, Item item, User user, User proxy,
               ServicePoint checkinServicePoint, ServicePoint checkoutServicePoint,
               DateTime originalDueDate, LoanPolicy loanPolicy,
               OverdueFinePolicy overdueFinePolicy,
               LostItemPolicy lostItemPolicy,
               Collection<Account> accounts) {

    requireNonNull(loanPolicy, "loanPolicy cannot be null");

    this.representation = representation;
    this.item = item;
    this.user = user;
    this.proxy = proxy;
    this.accounts = accounts;
    this.checkinServicePoint = checkinServicePoint;
    this.checkoutServicePoint = checkoutServicePoint;

    this.checkoutServicePointId = getProperty(representation, CHECKOUT_SERVICE_POINT_ID);
    this.checkinServicePointId = getProperty(representation, CHECKIN_SERVICE_POINT_ID);

    this.originalDueDate = originalDueDate == null ? getDueDate() : originalDueDate;

    this.loanPolicy = loanPolicy;
    this.overdueFinePolicy = overdueFinePolicy;
    this.lostItemPolicy = lostItemPolicy;

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
    defaultStatusAndAction(representation);
    return new Loan(representation, null, null, null, null, null, null,
      LoanPolicy.unknown(null), OverdueFinePolicy.unknown(null),
      LostItemPolicy.unknown(null), null);
  }

  JsonObject asJson() {
    return representation.copy();
  }

  public boolean hasAssociatedFeesAndFines() {
    return !getAccounts().isEmpty();
  }

  public boolean allFeesAndFinesClosed() {
    return getAccounts().stream().allMatch(Account::isClosed);
  }

  public Loan changeDueDate(DateTime newDueDate) {
    write(representation, DUE_DATE, newDueDate);

    return this;
  }

  public Loan changeDueDateChangedByRecall() {
    write(representation, "dueDateChangedByRecall", TRUE);

    return this;
  }

  private void changeReturnDate(DateTime returnDate) {
    write(representation, RETURN_DATE, returnDate);
  }

  private void changeSystemReturnDate(DateTime systemReturnDate) {
    write(representation, SYSTEM_RETURN_DATE, systemReturnDate);
  }

  public void changeAction(LoanAction action) {
    write(representation, LoanProperties.ACTION, action.getValue());
  }

  private void changeCheckInServicePointId(UUID servicePointId) {
    write(representation, "checkinServicePointId", servicePointId);
  }

  private void changeItemStatusToDeclaredLost() {
    Item item = getItem();
    if (item != null) {
      item.changeStatus(ItemStatus.DECLARED_LOST);
    }
    changeItemStatus(ItemStatus.DECLARED_LOST.getValue());
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
      return failedDueToServerError("Loan does not have a status");
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

  public boolean isClosed() {
    return StringUtils.equals(getStatus(), "Closed");
  }

  public boolean isOpen() {
    return StringUtils.equals(getStatus(), "Open");
  }

  public boolean wasDueDateChangedByRecall() {
    return getBooleanProperty(representation, "dueDateChangedByRecall");
  }

  private String getStatus() {
    return getNestedStringProperty(representation, STATUS, "name");
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  public Collection<Account> getAccounts() {
    return accounts;
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

  Loan replaceRepresentation(JsonObject newRepresentation) {
    return new Loan(newRepresentation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, loanPolicy, overdueFinePolicy,
      lostItemPolicy, accounts);
  }

  public Loan withItem(Item item) {
    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, loanPolicy, overdueFinePolicy,
      lostItemPolicy, accounts);
  }

  public User getUser() {
    return user;
  }

  public Loan withUser(User newUser) {
    return new Loan(representation, item, newUser, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, loanPolicy, overdueFinePolicy,
      lostItemPolicy, accounts);
  }

  Loan withPatronGroupAtCheckout(PatronGroup patronGroup) {
    if (nonNull(patronGroup)) {
      JsonObject patronGroupAtCheckout = new JsonObject()
        .put("id", patronGroup.getId())
        .put("name", patronGroup.getGroup());

      write(representation, LoanProperties.PATRON_GROUP_AT_CHECKOUT,
        patronGroupAtCheckout);
    }
    return this;
  }

  public User getProxy() {
    return proxy;
  }

  Loan withProxy(User newProxy) {
    return new Loan(representation, item, user, newProxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, loanPolicy, overdueFinePolicy,
      lostItemPolicy, accounts);
  }

  Loan withCheckinServicePoint(ServicePoint newCheckinServicePoint) {
    return new Loan(representation, item, user, proxy, newCheckinServicePoint,
      checkoutServicePoint, originalDueDate, loanPolicy, overdueFinePolicy,
      lostItemPolicy, accounts);
  }

  Loan withCheckoutServicePoint(ServicePoint newCheckoutServicePoint) {
    return new Loan(representation, item, user, proxy, checkinServicePoint,
      newCheckoutServicePoint, originalDueDate, loanPolicy, overdueFinePolicy,
      lostItemPolicy, accounts);
  }

  public Loan withAccounts(Collection<Account> newAccounts) {
    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, loanPolicy, overdueFinePolicy,
      lostItemPolicy, newAccounts);
  }

  public Loan withLoanPolicy(LoanPolicy newloanPolicy) {
    requireNonNull(newloanPolicy, "newloanPolicy cannot be null");

    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, newloanPolicy, overdueFinePolicy,
      lostItemPolicy, accounts);
  }

  public Loan withOverdueFinePolicy(OverdueFinePolicy newOverdueFinePolicy) {
    requireNonNull(newOverdueFinePolicy, "newOverdueFinePolicy cannot be null");

    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, loanPolicy, newOverdueFinePolicy,
      lostItemPolicy, accounts);
  }

  public Loan withLostItemPolicy(LostItemPolicy newLostItemPolicy) {
    requireNonNull(newLostItemPolicy, "newLostItemPolicy cannot be null");

    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, loanPolicy, overdueFinePolicy,
      newLostItemPolicy, accounts);
  }

  public String getLoanPolicyId() {
    return representation.getString("loanPolicyId");
  }

  public String getOverdueFinePolicyId() {
    return representation.getString("overdueFinePolicyId");
  }

  public String getLostItemPolicyId() {
    return representation.getString("lostItemPolicyId");
  }

  public LoanPolicy getLoanPolicy() {
    return loanPolicy;
  }

  public OverdueFinePolicy getOverdueFinePolicy() {
    return overdueFinePolicy;
  }

  public LostItemPolicy getLostItemPolicy() {
    return lostItemPolicy;
  }

  private void setLoanPolicyId(String newLoanPolicyId) {
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

  String getPatronGroupIdAtCheckout() {
    return  getProperty(representation, "patronGroupIdAtCheckout");
  }

  public Loan renew(DateTime dueDate, String basedUponLoanPolicyId) {
    changeAction(RENEWED);
    removeActionComment();
    setLoanPolicyId(basedUponLoanPolicyId);
    changeDueDate(dueDate);
    incrementRenewalCount();

    return this;
  }

  public Loan overrideRenewal(DateTime dueDate,
                              String basedUponLoanPolicyId,
                              String actionComment) {
    changeAction(RENEWED_THROUGH_OVERRIDE);
    setLoanPolicyId(basedUponLoanPolicyId);
    changeDueDate(dueDate);
    incrementRenewalCount();
    changeActionComment(actionComment);

    return this;
  }

  Loan checkIn(DateTime returnDateTime, DateTime systemReturnDateTime, UUID servicePointId) {
    changeAction(CHECKED_IN);
    removeActionComment();
    changeStatus("Closed");
    changeReturnDate(returnDateTime);
    changeSystemReturnDate(systemReturnDateTime);
    changeCheckInServicePointId(servicePointId);

    return this;
  }

  public Loan declareItemLost(String comment, DateTime dateTime) {
    changeAction(DECLARED_LOST);
    changeActionComment(comment);
    changeItemStatusToDeclaredLost();
    changeDeclaredLostDateTime(dateTime);
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
        loan.put(LoanProperties.ACTION, CHECKED_OUT.getValue());
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
    return !Objects.equals(originalDueDate, getDueDate());
  }

  public DateTime getSystemReturnDate() {
    return getDateTimeProperty(representation, SYSTEM_RETURN_DATE);
  }

  public DateTime getReturnDate() {
    return getDateTimeProperty(representation, RETURN_DATE);
  }

  public void changeItemStatus(String itemStatus) {
    representation.put(LoanProperties.ITEM_STATUS, itemStatus);
  }

  public void changeDeclaredLostDateTime(DateTime dateTime) {
    write(representation, LoanProperties.DECLARED_LOST_DATE, dateTime);
  }
}
