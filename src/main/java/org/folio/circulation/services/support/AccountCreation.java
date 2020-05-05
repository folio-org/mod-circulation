package org.folio.circulation.services.support;

import java.math.BigDecimal;

import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;

public final class AccountCreation {
  private final Loan loan;
  private final Item item;
  private final FeeFine feeFine;
  private final FeeFineOwner feeFineOwner;
  private final BigDecimal amount;
  private final String staffUserId;
  private final String currentServicePointId;

  private AccountCreation(Builder builder) {
    this.loan = builder.loan;
    this.item = builder.item;
    this.feeFine = builder.feeFine;
    this.feeFineOwner = builder.feeFineOwner;
    this.amount = builder.amount;
    this.staffUserId = builder.staffUserId;
    this.currentServicePointId = builder.currentServicePointId;
  }

  public Loan getLoan() {
    return loan;
  }

  public Item getItem() {
    return item;
  }

  public FeeFine getFeeFine() {
    return feeFine;
  }

  public FeeFineOwner getFeeFineOwner() {
    return feeFineOwner;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getStaffUserId() {
    return staffUserId;
  }

  public String getCurrentServicePointId() {
    return currentServicePointId;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private Loan loan;
    private Item item;
    private FeeFine feeFine;
    private FeeFineOwner feeFineOwner;
    private BigDecimal amount;
    private String staffUserId;
    private String currentServicePointId;

    private Builder() {}

    public Builder withLoan(Loan loan) {
      this.loan = loan;
      return this;
    }

    public Builder withItem(Item item) {
      this.item = item;
      return this;
    }

    public Builder withFeeFine(FeeFine feeFine) {
      this.feeFine = feeFine;
      return this;
    }

    public Builder withFeeFineOwner(FeeFineOwner feeFineOwner) {
      this.feeFineOwner = feeFineOwner;
      return this;
    }

    public Builder withAmount(BigDecimal amount) {
      this.amount = amount;
      return this;
    }

    public Builder withStaffUserId(String staffUserId) {
      this.staffUserId = staffUserId;
      return this;
    }

    public Builder withCurrentServicePointId(String currentServicePointId) {
      this.currentServicePointId = currentServicePointId;
      return this;
    }

    public AccountCreation build() {
      return new AccountCreation(this);
    }
  }
}
