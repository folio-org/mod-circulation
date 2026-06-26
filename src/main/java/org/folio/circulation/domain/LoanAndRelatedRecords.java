package org.folio.circulation.domain;


import java.time.ZoneId;
import java.time.ZoneOffset;

import org.folio.circulation.domain.configuration.CheckoutLockConfiguration;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.With;

@AllArgsConstructor
@With
@Getter
@ToString(onlyExplicitlyIncluded = true)
public class LoanAndRelatedRecords implements UserRelatedRecord {
  public static final String REASON_TO_OVERRIDE = "reasonToOverride";

  @ToString.Include
  private final Loan loan;
  private final Loan existingLoan;
  private final RequestQueue requestQueue;
  private final ZoneId timeZone;
  private final JsonObject logContextProperties;
  private final String loggedInUserId;
  private final TlrSettingsConfiguration tlrSettings;
  private final Request closedFilledRequest;
  private final CheckoutLockConfiguration checkoutLockConfiguration;
  private final String forceLoanPolicyId;

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, ZoneOffset.UTC);
  }

  public LoanAndRelatedRecords(Loan loan, String forceLoanPolicyId) {
    this(loan, ZoneOffset.UTC, forceLoanPolicyId);
  }

  public LoanAndRelatedRecords(Loan loan, Loan existingLoan) {
    this(loan, existingLoan, ZoneOffset.UTC);
  }

  public LoanAndRelatedRecords(Loan loan, Loan existingLoan, ZoneId timeZone) {
    this(loan, existingLoan, null, timeZone, new JsonObject(), null, null, null, null, null);
  }

  public LoanAndRelatedRecords(Loan loan, ZoneId timeZone) {
    this(loan, null, null, timeZone, new JsonObject(), null, null, null, null, null);
  }

  public LoanAndRelatedRecords(Loan loan, ZoneId timeZone, String forceLoanPolicyId) {
    this(loan, null, null, timeZone, new JsonObject(), null, null, null, null, forceLoanPolicyId);
  }

  public LoanAndRelatedRecords changeItemStatus(ItemStatus status) {
    return withItem(getItem().changeStatus(status));
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  public LoanAndRelatedRecords withItemEffectiveLocationIdAtCheckOut() {
    return withLoan(loan.changeItemEffectiveLocationIdAtCheckOut(getItem().getEffectiveLocationId()));
  }

  public Item getItem() {
    return getLoan().getItem();
  }

  public RequestQueue getRequestQueue() {
    return requestQueue;
  }

  public User getProxy() {
    return loan.getProxy();
  }

  @Override
  public String getUserId() {
    return loan.getUserId();
  }

  @Override
  public String getProxyUserId() {
    return loan.getProxyUserId();
  }

  @Override
  public User getUser() {
    return loan.getUser();
  }

  public boolean isCheckoutLockFeatureEnabled() {
    return this.getCheckoutLockConfiguration() != null && this.getCheckoutLockConfiguration().isCheckOutLockFeatureEnabled();
  }

}
