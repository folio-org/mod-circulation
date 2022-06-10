package org.folio.circulation.services.support;

import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;

import lombok.Builder;
import lombok.Getter;

@Builder(setterPrefix = "with")
@Getter
public final class CreateAccountCommand {
  private final Loan loan;
  private final Item item;
  private final FeeFine feeFine;
  private final FeeFineOwner feeFineOwner;
  private final FeeAmount amount;
  private final String staffUserId;
  private final boolean createdByAutomatedProcess;
  private final String currentServicePointId;
  private final String loanPolicyId;
  private final String overdueFinePolicyId;
  private final String lostItemFeePolicyId;
}
