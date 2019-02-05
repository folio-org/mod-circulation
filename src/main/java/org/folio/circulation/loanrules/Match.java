package org.folio.circulation.loanrules;

/**
 * Store the result of a rule match.
 */
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
  /** line number of the matching rule */
  @SuppressWarnings("squid:ClassVariableVisibilityCheck")  // Drools directly uses public fields
  public int lineNumber;
}
