package org.folio.circulation.domain;

import java.math.BigDecimal;

public final class FeeFineAccountAndAction {
  private final Loan loan;
  private final Item item;
  private final FeeFine feeFine;
  private final FeeFineOwner feeFineOwner;
  private final BigDecimal amount;
  private final User createdBy;
  private final String createdAt;

  private FeeFineAccountAndAction(Builder builder) {
    this.loan = builder.loan;
    this.item = builder.item;
    this.feeFine = builder.feeFine;
    this.feeFineOwner = builder.feeFineOwner;
    this.amount = builder.amount;
    this.createdBy = builder.createdBy;
    this.createdAt = builder.createdAt;
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

  public User getCreatedBy() {
    return createdBy;
  }

  public String getCreatedAt() {
    return createdAt;
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
    private User createdBy;
    private String createdAt;

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

    public Builder withCreatedBy(User createdBy) {
      this.createdBy = createdBy;
      return this;
    }

    public Builder withCreatedAt(String createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    public FeeFineAccountAndAction build() {
      return new FeeFineAccountAndAction(this);
    }
  }
}
