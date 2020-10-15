package org.folio.circulation.resources;

import org.folio.circulation.rules.CirculationRulesProcessor;

import io.vertx.core.http.HttpClient;

/**
 * The circulation rules engine calculates the loan policy based on
 * item type, loan type, patron type and shelving location.
 */
public class NoticeCirculationRulesEngineResource extends AbstractCirculationRulesEngineResource {

  public NoticeCirculationRulesEngineResource(String applyPath, String applyAllPath, HttpClient client) {
    super(applyPath, applyAllPath, client,
      CirculationRulesProcessor::getNoticePolicyAndMatch,
      CirculationRulesProcessor::getNoticePolicies);
  }

  @Override
  protected String getPolicyIdKey() {
    return "noticePolicyId";
  }
}
