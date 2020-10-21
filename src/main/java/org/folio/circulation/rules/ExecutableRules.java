package org.folio.circulation.rules;

import static org.folio.circulation.support.results.Result.of;

import org.folio.circulation.support.results.Result;

import lombok.Getter;

public class ExecutableRules {
  @Getter()
  private final String text;
  private final Drools drools;

  public ExecutableRules(String text, Drools drools) {
    this.text = text;
    this.drools = drools;
  }

  public Result<CirculationRuleMatch> determineLoanPolicy(RulesExecutionParameters parameters) {
    return of(() -> drools.loanPolicy(parameters.toMap(), parameters.getLocation()));
  }

  public Result<CirculationRuleMatch> determineRequestPolicy(RulesExecutionParameters parameters) {
    return of(() -> drools.requestPolicy(parameters.toMap(), parameters.getLocation()));
  }

  public Result<CirculationRuleMatch> determineNoticePolicy(RulesExecutionParameters parameters) {
    return of(() -> drools.noticePolicy(parameters.toMap(), parameters.getLocation()));
  }

  public Result<CirculationRuleMatch> determineLostItemPolicy(RulesExecutionParameters parameters) {
    return of(() -> drools.lostItemPolicy(parameters.toMap(), parameters.getLocation()));
  }

  public Result<CirculationRuleMatch> determineOverduePolicy(RulesExecutionParameters parameters) {
    return of(() -> drools.overduePolicy(parameters.toMap(), parameters.getLocation()));
  }
}
