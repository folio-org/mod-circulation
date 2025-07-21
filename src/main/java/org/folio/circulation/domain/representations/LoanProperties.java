package org.folio.circulation.domain.representations;

public class LoanProperties {
  private LoanProperties() { }

  public static final String ITEM_ID = "itemId";
  public static final String USER_ID = "userId";
  public static final String STATUS = "status";
  public static final String ACTION = "action";
  public static final String ITEM_STATUS = "itemStatus";
  public static final String DUE_DATE = "dueDate";
  public static final String RETURN_DATE = "returnDate";
  public static final String SYSTEM_RETURN_DATE = "systemReturnDate";
  public static final String CHECKIN_SERVICE_POINT_ID = "checkinServicePointId";
  public static final String CHECKOUT_SERVICE_POINT_ID = "checkoutServicePointId";
  public static final String ACTION_COMMENT = "actionComment";
  public static final String ITEM_LOCATION_ID_AT_CHECKOUT = "itemEffectiveLocationIdAtCheckOut";
  public static final String BORROWER = "borrower";
  public static final String LOAN_POLICY = "loanPolicy";
  public static final String OVERDUE_FINE_POLICY = "overdueFinePolicy";
  public static final String LOST_ITEM_POLICY = "lostItemPolicy";
  public static final String LOAN_POLICY_ID = "loanPolicyId";
  public static final String OVERDUE_FINE_POLICY_ID = "overdueFinePolicyId";
  public static final String LOST_ITEM_POLICY_ID = "lostItemPolicyId";
  public static final String FEESANDFINES = "feesAndFines";
  public static final String PATRON_GROUP_ID_AT_CHECKOUT = "patronGroupIdAtCheckout";
  public static final String PATRON_GROUP_AT_CHECKOUT = "patronGroupAtCheckout";
  public static final String DECLARED_LOST_DATE = "declaredLostDate";
  public static final String AGED_TO_LOST_DATE = "agedToLostDate";
  public static final String CLAIMED_RETURNED_DATE = "claimedReturnedDate";
  public static final String LOAN_DATE = "loanDate";
  public static final String AGED_TO_LOST_DELAYED_BILLING = "agedToLostDelayedBilling";
  public static final String LOST_ITEM_HAS_BEEN_BILLED = "lostItemHasBeenBilled";
  public static final String DATE_LOST_ITEM_SHOULD_BE_BILLED = "dateLostItemShouldBeBilled";
  public static final String METADATA = "metadata";
  public static final String UPDATED_BY_USER_ID = "updatedByUserId";
  public static final String FOR_USE_AT_LOCATION = "forUseAtLocation";
  public static final String AT_LOCATION_USE_STATUS = "status";
  public static final String AT_LOCATION_USE_STATUS_DATE = "statusDate";
  public static final String USAGE_STATUS_IN_USE = "In use";
  public static final String USAGE_STATUS_HELD = "Held";
  public static final String USAGE_STATUS_RETURNED = "Returned";
  public static final String CREATED_DATE = "createdDate";
  public static final String REMINDERS = "reminders";
  public static final String LAST_FEE_BILLED = "lastFeeBilled";
  public static final String BILL_NUMBER = "number";
  public static final String BILL_DATE = "date";
}
