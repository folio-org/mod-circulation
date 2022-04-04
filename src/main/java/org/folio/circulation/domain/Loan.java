package org.folio.circulation.domain;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;
import static org.folio.circulation.domain.FeeAmount.noFeeAmount;
import static org.folio.circulation.domain.LoanAction.CHECKED_IN;
import static org.folio.circulation.domain.LoanAction.CHECKED_OUT;
import static org.folio.circulation.domain.LoanAction.CLOSED_LOAN;
import static org.folio.circulation.domain.LoanAction.DECLARED_LOST;
import static org.folio.circulation.domain.LoanAction.ITEM_AGED_TO_LOST;
import static org.folio.circulation.domain.LoanAction.MISSING;
import static org.folio.circulation.domain.LoanAction.RENEWED;
import static org.folio.circulation.domain.LoanAction.RENEWED_THROUGH_OVERRIDE;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION_COMMENT;
import static org.folio.circulation.domain.representations.LoanProperties.AGED_TO_LOST_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.AGED_TO_LOST_DELAYED_BILLING;
import static org.folio.circulation.domain.representations.LoanProperties.CHECKIN_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.LoanProperties.CHECKOUT_SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.LoanProperties.CLAIMED_RETURNED_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.DATE_LOST_ITEM_SHOULD_BE_BILLED;
import static org.folio.circulation.domain.representations.LoanProperties.DECLARED_LOST_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.DUE_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_LOCATION_ID_AT_CHECKOUT;
import static org.folio.circulation.domain.representations.LoanProperties.LOAN_POLICY_ID;
import static org.folio.circulation.domain.representations.LoanProperties.LOST_ITEM_HAS_BEEN_BILLED;
import static org.folio.circulation.domain.representations.LoanProperties.LOST_ITEM_POLICY_ID;
import static org.folio.circulation.domain.representations.LoanProperties.METADATA;
import static org.folio.circulation.domain.representations.LoanProperties.OVERDUE_FINE_POLICY_ID;
import static org.folio.circulation.domain.representations.LoanProperties.RETURN_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.STATUS;
import static org.folio.circulation.domain.representations.LoanProperties.SYSTEM_RETURN_DATE;
import static org.folio.circulation.domain.representations.LoanProperties.UPDATED_BY_USER_ID;
import static org.folio.circulation.domain.representations.LoanProperties.USER_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimePropertyByPath;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.remove;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.mostRecentDate;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor(access = PRIVATE)
public class Loan implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject representation;
  @NonNull private final Item item;
  private final User user;
  private final User proxy;
  private final ServicePoint checkinServicePoint;
  private final ServicePoint checkoutServicePoint;
  private final ZonedDateTime originalDueDate;
  private final ZonedDateTime previousDueDate;
  private final Policies policies;
  private final Collection<Account> accounts;

  public static Loan from(JsonObject representation) {
    defaultStatusAndAction(representation);
    final LoanPolicy loanPolicy = LoanPolicy.unknown(
      getProperty(representation, LOAN_POLICY_ID));
    final OverdueFinePolicy overdueFinePolicy = OverdueFinePolicy.unknown(
      getProperty(representation, OVERDUE_FINE_POLICY_ID));
    final LostItemPolicy lostItemPolicy = LostItemPolicy.unknown(
      getProperty(representation, LOST_ITEM_POLICY_ID));

    return new Loan(representation, Item.unknown(getProperty(representation, "itemId")),
      null, null, null, null,
      getDateTimeProperty(representation, DUE_DATE), getDateTimeProperty(representation, DUE_DATE),
      new Policies(loanPolicy, overdueFinePolicy, lostItemPolicy), emptyList());
  }

  public JsonObject asJson() {
    return representation.copy();
  }

  public boolean hasAssociatedFeesAndFines() {
    return !getAccounts().isEmpty();
  }

  public boolean allFeesAndFinesClosed() {
    return getAccounts().stream().allMatch(Account::isClosed);
  }

  public Loan changeDueDate(ZonedDateTime newDueDate) {
    write(representation, DUE_DATE, newDueDate.withZoneSameInstant(UTC));

    return this;
  }

  public Loan setDueDateChangedByRecall() {
    return setDueDateChangedByRecall(TRUE);
  }

  public Loan unsetDueDateChangedByRecall() {
    return setDueDateChangedByRecall(FALSE);
  }

  public Loan setDueDateChangedByRecall(Boolean value) {
    write(representation, "dueDateChangedByRecall", value);

    return this;
  }

  private Loan changeReturnDate(ZonedDateTime returnDate) {
    write(representation, RETURN_DATE, returnDate);

    return this;
  }

  private Loan changeSystemReturnDate(ZonedDateTime systemReturnDate) {
    write(representation, SYSTEM_RETURN_DATE, systemReturnDate);

    return this;
  }

  public Loan changeAction(LoanAction action) {
    write(representation, LoanProperties.ACTION, action.getValue());

    return this;
  }

  public String getAction() {
    return getProperty(representation, ACTION);
  }

  private Loan changeCheckInServicePointId(UUID servicePointId) {
    write(representation, "checkinServicePointId", servicePointId);

    return this;
  }

  public Loan changeItemStatusForItemAndLoan(ItemStatusName itemStatus) {
    return changeItemStatus(itemStatus.getName())
      .withItem(item.changeStatus(itemStatus));
  }

  public Loan changeItemEffectiveLocationIdAtCheckOut(String locationId) {
    representation.put(ITEM_LOCATION_ID_AT_CHECKOUT, locationId);

    return this;
  }

  public Loan changeActionComment(String comment) {
    representation.put(ACTION_COMMENT, comment);

    return this;
  }

  public Loan removeActionComment() {
    representation.remove(ACTION_COMMENT);

    return this;
  }

  public String getActionComment() {
    return getProperty(representation, ACTION_COMMENT);
  }

  public Result<Void> isValidStatus() {
    if (!representation.containsKey(STATUS)) {
      return failedDueToServerError("Loan does not have a status");
    }

    // Provided status name is not present in the enum
    if (getStatus() == null) {
      return failedValidation("Loan status must be \"Open\" or \"Closed\"",
        STATUS, getStatusName());
    }

    return succeeded(null);
  }

  public Result<Void> openLoanHasUserId() {
    if (isOpen() && getUserId() == null) {
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
    return getStatus() == LoanStatus.CLOSED;
  }

  public boolean isOpen() {
    return getStatus() == LoanStatus.OPEN;
  }

  public boolean wasDueDateChangedByRecall() {
    return getBooleanProperty(representation, "dueDateChangedByRecall");
  }

  private LoanStatus getStatus() {
    return LoanStatus.fromValue(getStatusName());
  }

  private String getStatusName() {
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

  public ZonedDateTime getLoanDate() {
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

  public Loan replaceRepresentation(JsonObject newRepresentation) {
    return new Loan(newRepresentation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate, policies, accounts);
  }

  public Loan withItem(@NonNull Item newItem) {
    JsonObject newRepresentation = representation.copy();

    if (newItem.isFound()) {
      newRepresentation.put("itemId", newItem.getItemId());
    }

    return new Loan(newRepresentation, newItem, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate, policies, accounts);
  }

  public User getUser() {
    return user;
  }

  public Loan withUser(User newUser) {
    JsonObject newRepresentation = representation.copy();

    if (newUser != null) {
      newRepresentation.put("userId", newUser.getId());
    }

    return new Loan(newRepresentation, item, newUser, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate, policies, accounts);
  }

  public Loan withPatronGroupAtCheckout(PatronGroup patronGroup) {
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
    JsonObject newRepresentation = representation.copy();

    if (newProxy != null) {
      newRepresentation.put("proxyUserId", newProxy.getId());
    }

    return new Loan(newRepresentation, item, user, newProxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate, policies, accounts);
  }

  public Loan withCheckinServicePoint(ServicePoint newCheckinServicePoint) {
    return new Loan(representation, item, user, proxy, newCheckinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate, policies, accounts);
  }

  public Loan withCheckoutServicePoint(ServicePoint newCheckoutServicePoint) {
    return new Loan(representation, item, user, proxy, checkinServicePoint,
      newCheckoutServicePoint, originalDueDate, previousDueDate, policies, accounts);
  }

  public Loan withAccounts(Collection<Account> newAccounts) {
    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate, policies, newAccounts);
  }

  public Loan withLoanPolicy(LoanPolicy newLoanPolicy) {
    requireNonNull(newLoanPolicy, "newLoanPolicy cannot be null");

    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate,
      policies.withLoanPolicy(newLoanPolicy), accounts);
  }

  public Loan withOverdueFinePolicy(OverdueFinePolicy newOverdueFinePolicy) {
    requireNonNull(newOverdueFinePolicy, "newOverdueFinePolicy cannot be null");

    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate,
      policies.withOverdueFinePolicy(newOverdueFinePolicy), accounts);
  }

  public Loan withLostItemPolicy(LostItemPolicy newLostItemPolicy) {
    requireNonNull(newLostItemPolicy, "newLostItemPolicy cannot be null");

    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate,
      policies.withLostItemPolicy(newLostItemPolicy), accounts);
  }

  public String getLoanPolicyId() {
    return policies.getLoanPolicy().getId();
  }

  public String getOverdueFinePolicyId() {
    return policies.getOverdueFinePolicy().getId();
  }

  public String getLostItemPolicyId() {
    return policies.getLostItemPolicy().getId();
  }

  public LoanPolicy getLoanPolicy() {
    return policies.getLoanPolicy();
  }

  public OverdueFinePolicy getOverdueFinePolicy() {
    return policies.getOverdueFinePolicy();
  }

  public LostItemPolicy getLostItemPolicy() {
    return policies.getLostItemPolicy();
  }

  private Loan setLoanPolicyId(String newLoanPolicyId) {
    if (newLoanPolicyId != null) {
      representation.put("loanPolicyId", newLoanPolicyId);
    }

    return this;
  }

  public ServicePoint getCheckinServicePoint() {
    return this.checkinServicePoint;
  }

  public ServicePoint getCheckoutServicePoint() {
    return this.checkoutServicePoint;
  }

  public String getPatronGroupIdAtCheckout() {
    return  getProperty(representation, "patronGroupIdAtCheckout");
  }

  public Loan renew(ZonedDateTime dueDate, String basedUponLoanPolicyId) {
    return changeAction(RENEWED)
      .removeActionComment()
      .setLoanPolicyId(basedUponLoanPolicyId)
      .changeDueDate(dueDate)
      .incrementRenewalCount();
  }

  public Loan overrideRenewal(ZonedDateTime dueDate, String basedUponLoanPolicyId,
    String actionComment) {

    return changeAction(RENEWED_THROUGH_OVERRIDE)
      .setLoanPolicyId(basedUponLoanPolicyId)
      .changeDueDate(dueDate)
      .incrementRenewalCount()
      .changeActionComment(actionComment);
  }

  private Loan checkIn(LoanAction action, ZonedDateTime returnDateTime,
    ZonedDateTime systemReturnDateTime, UUID servicePointId) {

    return closeLoan(action)
      .changeReturnDate(returnDateTime)
      .changeSystemReturnDate(systemReturnDateTime)
      .changeCheckInServicePointId(servicePointId);
  }

  Loan checkIn(ZonedDateTime returnDateTime, ZonedDateTime systemReturnDateTime, UUID servicePointId) {
    return checkIn(CHECKED_IN, returnDateTime, systemReturnDateTime,
      servicePointId);
  }

  Loan resolveClaimedReturned(LoanAction resolveAction,
    ZonedDateTime returnDateTime, ZonedDateTime systemReturnDateTime,
    UUID servicePointId) {

    return checkIn(resolveAction, returnDateTime, systemReturnDateTime, servicePointId);
  }


  public Loan declareItemLost(String comment, ZonedDateTime dateTime) {
    return changeAction(DECLARED_LOST)
      .changeActionComment(comment)
      .changeItemStatusForItemAndLoan(ItemStatusName.DECLARED_LOST)
      .changeDeclaredLostDateTime(dateTime);
  }

  public boolean isDeclaredLost() {
    return hasItemWithStatus(ItemStatusName.DECLARED_LOST);
  }

  public boolean isAgedToLost() {
    return hasItemWithStatus(ItemStatusName.AGED_TO_LOST);
  }

  public boolean isItemLost() {
    return getItem().getStatus().isLostNotResolved();
  }

  public boolean isClaimedReturned() {
    return hasItemWithStatus(ItemStatusName.CLAIMED_RETURNED);
  }

  public boolean isRenewed() {
    String action = getAction();
    if (action != null) {
      LoanAction loanAction = LoanAction.from(action);
      return loanAction == RENEWED || loanAction == RENEWED_THROUGH_OVERRIDE;
    }
    return false;
  }

  public boolean hasItemWithStatus(ItemStatusName itemStatus) {
    return hasItemWithAnyStatus(itemStatus);
  }

  public boolean hasItemWithAnyStatus(ItemStatusName... itemStatuses) {
    return Stream.of(itemStatuses).anyMatch(item::isInStatus);
  }

  private Loan incrementRenewalCount() {
    write(representation, "renewalCount", getRenewalCount() + 1);

    return this;
  }

  public Integer getRenewalCount() {
    return getIntegerProperty(representation, "renewalCount", 0);
  }

  public ZonedDateTime getDueDate() {
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
    return getProperty(representation, CHECKOUT_SERVICE_POINT_ID);
  }

  public String getCheckInServicePointId() {
    return getProperty(representation, CHECKIN_SERVICE_POINT_ID);
  }

  public boolean hasDueDateChanged() {
    return !isSameMillis(originalDueDate, getDueDate());
  }

  public ZonedDateTime getSystemReturnDate() {
    return getDateTimeProperty(representation, SYSTEM_RETURN_DATE);
  }

  public ZonedDateTime getReturnDate() {
    return getDateTimeProperty(representation, RETURN_DATE);
  }

  public Loan changeItemStatus(String itemStatus) {
    representation.put(LoanProperties.ITEM_STATUS, itemStatus);

    return this;
  }

  public Loan changeDeclaredLostDateTime(ZonedDateTime dateTime) {
    write(representation, DECLARED_LOST_DATE, dateTime);

    return this;
  }

  public ZonedDateTime getDeclareLostDateTime() {
    return getDateTimeProperty(representation, DECLARED_LOST_DATE);
  }

  public ZonedDateTime getAgedToLostDateTime() {
    return getDateTimePropertyByPath(representation, AGED_TO_LOST_DELAYED_BILLING,
      AGED_TO_LOST_DATE);
  }

  public boolean isOverdue() {
    return isOverdue(ClockUtil.getZonedDateTime());
  }

  public boolean isOverdue(ZonedDateTime systemTime) {
    ZonedDateTime dueDate = getDueDate();

    return ObjectUtils.allNotNull(dueDate, systemTime)
      && isBeforeMillis(dueDate, systemTime);
  }

  public Loan claimItemReturned(String comment, ZonedDateTime claimedReturnedDate) {
    final Loan newLoan = StringUtils.isNotBlank(comment)
      ? changeActionComment(comment)
      : this;

    return newLoan
      .changeAction(LoanAction.CLAIMED_RETURNED)
      .changeItemStatusForItemAndLoan(ItemStatusName.CLAIMED_RETURNED)
      .changeClaimedReturnedDate(claimedReturnedDate);
  }

  private Loan changeClaimedReturnedDate(ZonedDateTime claimedReturnedDate) {
    write(representation, CLAIMED_RETURNED_DATE, claimedReturnedDate);

    return this;
  }

  public Loan closeLoan(LoanAction action) {
    return closeLoan()
      .changeAction(action)
      .removeActionComment();
  }

  public Loan closeLoan(LoanAction action, String comment) {
    return closeLoan()
      .changeAction(action)
      .changeActionComment(comment);
  }

  private Loan closeLoan() {
    representation.put(STATUS,
      new JsonObject().put("name", LoanStatus.CLOSED.getValue()));

    return this;
  }

  public Loan markItemMissing(String comment) {
    return changeItemStatusForItemAndLoan(ItemStatusName.MISSING)
      .closeLoan(MISSING, comment);
  }

  public FeeAmount getRemainingFeeFineAmount() {
    if (accounts == null) {
      return FeeAmount.noFeeAmount();
    }

    return accounts.stream()
      .filter(Account::isOpen)
      .map(Account::getRemaining)
      .reduce(FeeAmount::add)
      .orElse(noFeeAmount());
  }

  public Loan closeLoanAsLostAndPaid() {
    return closeLoan(CLOSED_LOAN)
      .changeItemStatusForItemAndLoan(ItemStatusName.LOST_AND_PAID);
  }

  public Loan copy() {
    final JsonObject representationCopy = representation.copy();

    return new Loan(representationCopy, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate, policies, accounts);
  }

  public Loan ageOverdueItemToLost(ZonedDateTime ageToLostDate) {
    return changeAction(ITEM_AGED_TO_LOST)
      .removeActionComment()
      .changeItemStatusForItemAndLoan(ItemStatusName.AGED_TO_LOST)
      .setAgedToLostDate(ageToLostDate);
  }

  public void setAgedToLostDelayedBilling(boolean hasBeenBilled, ZonedDateTime whenToBill) {
    writeByPath(representation, hasBeenBilled, AGED_TO_LOST_DELAYED_BILLING,
      LOST_ITEM_HAS_BEEN_BILLED);
    writeByPath(representation, whenToBill, AGED_TO_LOST_DELAYED_BILLING,
      DATE_LOST_ITEM_SHOULD_BE_BILLED);
  }

  public Loan setLostItemHasBeenBilled() {
    writeByPath(representation, true, AGED_TO_LOST_DELAYED_BILLING,
      LOST_ITEM_HAS_BEEN_BILLED);

    return this;
  }

  public Loan removeAgedToLostBillingInfo() {
    final JsonObject billingInfo = representation
      .getJsonObject(AGED_TO_LOST_DELAYED_BILLING);

    remove(billingInfo, LOST_ITEM_HAS_BEEN_BILLED);
    remove(billingInfo, DATE_LOST_ITEM_SHOULD_BE_BILLED);

    return this;
  }

  private Loan setAgedToLostDate(ZonedDateTime agedToLostDate) {
    writeByPath(representation, agedToLostDate, AGED_TO_LOST_DELAYED_BILLING,
      AGED_TO_LOST_DATE);

    return this;
  }

  public Loan removePreviousAction() {
    representation.put(LoanProperties.ACTION, "");

    return this;
  }

  public ZonedDateTime getLostDate() {
    return mostRecentDate(getDeclareLostDateTime(), getAgedToLostDateTime());
  }

  public String getUpdatedByUserId() {
    return getNestedStringProperty(representation, METADATA, UPDATED_BY_USER_ID);
  }

  public Loan setPreviousDueDate(ZonedDateTime previousDueDate) {
    return new Loan(representation, item, user, proxy, checkinServicePoint,
      checkoutServicePoint, originalDueDate, previousDueDate, policies, accounts);
  }

  public ZonedDateTime getPreviousDueDate() {
    return previousDueDate;
  }

  public ItemStatusName getItemStatus() {
    return item.getStatus().getName();
  }

  public String getItemStatusName() {
    return item.getStatusName();
  }
}
