package org.folio.circulation.services.feefine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.representations.AccountPaymentStatus;
import org.folio.circulation.domain.representations.StoredFeeFineAction;

public final class AccountRefundContext {
  private Account account;
  private User user;
  private ServicePoint servicePoint;
  private final List<StoredFeeFineAction> actions = new ArrayList<>();

  public AccountRefundContext(Account account) {
    this.account = account;
  }

  public Account getAccount() {
    return account;
  }

  public void closeAccount(AccountPaymentStatus paymentStatus) {
    account = account.close(paymentStatus);
  }

  public void setPaymentStatus(AccountPaymentStatus paymentStatus) {
    account = account.withPaymentStatus(paymentStatus);
  }

  public User getUser() {
    return user;
  }

  public AccountRefundContext withUser(User user) {
    this.user = user;
    return this;
  }

  public String getServicePointName() {
    return servicePoint != null ? servicePoint.getName() : "";
  }

  public AccountRefundContext withServicePoint(ServicePoint servicePoint) {
    this.servicePoint = servicePoint;
    return this;
  }

  public void subtractRemainingAmount(FeeAmount toSubtract) {
    account = account.subtractRemainingAmount(toSubtract);
  }

  public void addRemainingAmount(FeeAmount toSubtract) {
    account = account.addRemainingAmount(toSubtract);
  }

  public void addActions(StoredFeeFineAction action) {
    this.actions.add(action);
  }

  public List<StoredFeeFineAction> getActions() {
    return Collections.unmodifiableList(actions);
  }
}
