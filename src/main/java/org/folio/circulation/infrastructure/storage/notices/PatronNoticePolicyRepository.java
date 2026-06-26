package org.folio.circulation.infrastructure.storage.notices;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.infrastructure.storage.CirculationPolicyRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class PatronNoticePolicyRepository extends CirculationPolicyRepository<PatronNoticePolicy> {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final Function<JsonObject, Result<PatronNoticePolicy>> patronNoticePolicyMapper;

  public PatronNoticePolicyRepository(Clients clients) {
    this(clients, new PatronNoticePolicyMapper());
  }

  private PatronNoticePolicyRepository(
    Clients clients,
    Function<JsonObject, Result<PatronNoticePolicy>> patronNoticePolicyMapper) {
    super(clients.patronNoticePolicesStorageClient(), clients);
    this.patronNoticePolicyMapper = patronNoticePolicyMapper;
  }

  @Override
  protected String getPolicyNotFoundErrorMessage(String policyId) {
    return String.format("Notice policy %s could not be found, please check circulation rules", policyId);
  }

  @Override
  protected Result<PatronNoticePolicy> toPolicy(JsonObject representation,
    AppliedRuleConditions ruleConditionsEntity) {

    return patronNoticePolicyMapper.apply(representation);
  }

  @Override
  protected CompletableFuture<Result<CirculationRuleMatch>> getPolicyAndMatch(
    RulesExecutionParameters rulesExecutionParameters) {

    log.debug("getPolicyAndMatch:: parameters rulesExecutionParameters: {}", rulesExecutionParameters);
    return circulationRulesProcessor.getNoticePolicyAndMatch(rulesExecutionParameters);
  }
}
