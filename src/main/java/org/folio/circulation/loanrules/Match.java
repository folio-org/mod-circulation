package org.folio.circulation.loanrules;

/**
 * Store the result of a rule match.
 */
public class Match {
  /** loan policy of the matching rule */
  public String loanPolicyId;
  /** line number of the matching rule */
  public int lineNumber;
}
