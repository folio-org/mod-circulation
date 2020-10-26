package org.folio.circulation.rules;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.results.Result.of;
import static org.slf4j.LoggerFactory.getLogger;

import java.lang.invoke.MethodHandles;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.folio.circulation.domain.Location;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;

import io.vertx.core.MultiMap;
import lombok.Getter;

public class ExecutableRules {
  private static final Logger log = getLogger(MethodHandles.lookup().lookupClass());

  @Getter()
  private final String text;
  private final Drools drools;

  public ExecutableRules(String text, Drools drools) {
    this.text = text;
    this.drools = drools;
  }

  public Result<CirculationRuleMatch> determineLoanPolicy(RulesExecutionParameters parameters) {
    return determinePolicy(parameters, drools::loanPolicy, "loan policy");
  }

  public Result<CirculationRuleMatch> determineRequestPolicy(RulesExecutionParameters parameters) {
    return determinePolicy(parameters, drools::requestPolicy, "request policy");
  }

  public Result<CirculationRuleMatch> determineNoticePolicy(RulesExecutionParameters parameters) {
    return determinePolicy(parameters, drools::noticePolicy, "notice policy");
  }

  public Result<CirculationRuleMatch> determineLostItemPolicy(RulesExecutionParameters parameters) {
    return determinePolicy(parameters, drools::lostItemPolicy, "lost item policy");
  }

  public Result<CirculationRuleMatch> determineOverduePolicy(RulesExecutionParameters parameters) {
    return determinePolicy(parameters, drools::overduePolicy, "overdude policy");
  }

  private Result<CirculationRuleMatch> determinePolicy(RulesExecutionParameters parameters,
    BiFunction<MultiMap, Location, CirculationRuleMatch> droolsExecutor, String policyType) {

    if (log.isInfoEnabled()) {
      log.info("Executing circulation rules: `{}` with parameters: `{}` to determine {}",
        text, parameters, policyType);
    }

    return of(() -> droolsExecutor.apply(parameters.toMap(), parameters.getLocation()))
      .failWhen(this::noMatch, fail(parameters, policyType));
  }

  private Function<CirculationRuleMatch, HttpFailure> fail(
    RulesExecutionParameters parameters, String policyType) {

    return match -> new ServerErrorFailure(format(
      "Executing circulation rules: `%s` with parameters: `%s` to determine %s did not find a match",
      text, parameters, policyType));
  }

  private Result<Boolean> noMatch(CirculationRuleMatch match) {
    return of(() -> match == null || isBlank(match.getPolicyId()));
  }
}
