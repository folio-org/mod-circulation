package org.folio.circulation.rules;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.results.Result.of;

import java.lang.invoke.MethodHandles;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Location;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.MultiMap;
import lombok.Getter;

public class ExecutableRules {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static final String MATCH_FAIL_MSG =
    "Executing circulation rules: `%s` with parameters: `%s` to determine %s did not find a match";
  public static final String MATCH_FAIL_MSG_REGEX
    = "Executing circulation rules: `.*` with parameters: `.*` to determine .* did not find a match";

  @Getter()
  private final String text;
  private final Drools drools;

  public ExecutableRules(String text, Drools drools) {
    this.text = text;
    this.drools = drools;
  }

  public Result<CirculationRuleMatch> determineLoanPolicy(RulesExecutionParameters parameters) {
    log.debug("determineLoanPolicy:: parameters parameters: {}", parameters);

    return determinePolicy(parameters, drools::loanPolicy, "loan policy");
  }

  public Result<CirculationRuleMatch> determineRequestPolicy(RulesExecutionParameters parameters) {
    log.debug("determineRequestPolicy:: parameters parameters: {}", parameters);

    return determinePolicy(parameters, drools::requestPolicy, "request policy");
  }

  public Result<CirculationRuleMatch> determineNoticePolicy(RulesExecutionParameters parameters) {
    log.debug("determineNoticePolicy:: parameters parameters: {}", parameters);

    return determinePolicy(parameters, drools::noticePolicy, "notice policy");
  }

  public Result<CirculationRuleMatch> determineLostItemPolicy(RulesExecutionParameters parameters) {
    log.debug("determineLostItemPolicy:: parameters parameters: {}", parameters);

    return determinePolicy(parameters, drools::lostItemPolicy, "lost item policy");
  }

  public Result<CirculationRuleMatch> determineOverduePolicy(RulesExecutionParameters parameters) {
    log.debug("determineOverduePolicy:: parameters parameters: {}", parameters);

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

    return match -> new ServerErrorFailure(format(MATCH_FAIL_MSG, text, parameters, policyType));
  }

  private Result<Boolean> noMatch(CirculationRuleMatch match) {
    return of(() -> match == null || isBlank(match.getPolicyId()));
  }
}
