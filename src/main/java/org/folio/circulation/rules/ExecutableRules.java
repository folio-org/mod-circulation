package org.folio.circulation.rules;

import static org.folio.circulation.support.results.Result.of;

import java.util.function.BiFunction;

import org.folio.circulation.domain.Location;
import org.folio.circulation.support.results.Result;

import io.vertx.core.MultiMap;
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
    return determinePolicy(parameters, drools::loanPolicy);
  }

  public Result<CirculationRuleMatch> determineRequestPolicy(RulesExecutionParameters parameters) {
    return determinePolicy(parameters, drools::requestPolicy);
  }

  public Result<CirculationRuleMatch> determineNoticePolicy(RulesExecutionParameters parameters) {
    return determinePolicy(parameters, drools::noticePolicy);
  }

  public Result<CirculationRuleMatch> determineLostItemPolicy(RulesExecutionParameters parameters) {
    return determinePolicy(parameters, drools::lostItemPolicy);
  }

  public Result<CirculationRuleMatch> determineOverduePolicy(RulesExecutionParameters parameters) {
    return determinePolicy(parameters, drools::overduePolicy);
  }

  private Result<CirculationRuleMatch> determinePolicy(RulesExecutionParameters parameters,
    BiFunction<MultiMap, Location, CirculationRuleMatch> droolsExecutor) {

    return of(() -> droolsExecutor.apply(parameters.toMap(), parameters.getLocation()));
  }
}
