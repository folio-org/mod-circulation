package org.folio.circulation.rules;

import lombok.ToString;

/**
 * Store the result of a rule match.
 */
@ToString
public class Match {
  /** loan policy of the matching rule */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String loanPolicyId;
  /** request policy of the matching rule */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String requestPolicyId;
  /** notice policy of the matching rule */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String noticePolicyId;
  /** overdue policy of the matching rule */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String overduePolicyId;
  /** lost item policy of the matching rule */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public String lostItemPolicyId;
  /** line number of the matching rule */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public int lineNumber;
}
