package org.folio.circulation.infrastructure.storage.notices;

import java.util.function.Function;

import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.notice.PatronNoticePolicy;
import org.folio.circulation.infrastructure.storage.CirculationPolicyRepository;
import org.folio.circulation.resources.CirculationRulesProcessor;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.Drools;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

public class PatronNoticePolicyRepository extends CirculationPolicyRepository<PatronNoticePolicy> {
  private final Function<JsonObject, Result<PatronNoticePolicy>> patronNoticePolicyMapper;

  public PatronNoticePolicyRepository(Clients clients) {
    this(clients, new PatronNoticePolicyMapper());
  }

  private PatronNoticePolicyRepository(
    Clients clients,
    Function<JsonObject, Result<PatronNoticePolicy>> patronNoticePolicyMapper) {
    super(clients.locationsStorage(), clients.patronNoticePolicesStorageClient(), clients);
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
  protected String fetchPolicyId(JsonObject jsonObject) {
    return jsonObject.getString("noticePolicyId");
  }

  @Override
  protected CirculationRuleMatch getPolicyAndMatch(Drools drools, MultiMap params, Location location) {
    return CirculationRulesProcessor.getNoticePolicyAndMatch(drools, params, location);
  }
}
