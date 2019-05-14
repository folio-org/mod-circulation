package org.folio.circulation.domain.representations;

public class LoanProperties {
  private LoanProperties() { }

  public static final String ITEM_ID = "itemId";
  public static final String USER_ID = "userId";
  public static final String STATUS = "status";
  public static final String ACTION = "action";
  public static final String DUE_DATE = "dueDate";
  public static final String RETURN_DATE = "returnDate";
  public static final String SYSTEM_RETURN_DATE = "systemReturnDate";
  public static final String CHECKIN_SERVICE_POINT_ID = "checkinServicePointId";
  public static final String CHECKOUT_SERVICE_POINT_ID = "checkoutServicePointId";
  public static final String ACTION_COMMENT = "actionComment";
  public static final String BORROWER = "borrower";
}
