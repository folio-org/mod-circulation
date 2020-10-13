package org.folio.circulation.resources;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Location;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.Drools;
import org.folio.circulation.rules.Text2Drools;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class CirculationRulesProcessor {
  protected static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static CirculationRulesProcessor circulationRulesProcessor = new CirculationRulesProcessor();

  /** after this time the rules get loaded before executing the circulation rules engine */
  private static long maxAgeInMilliseconds = 5000;
  /** after this time the circulation rules engine is executed first for a fast reply
   * and then the circulation rules get reloaded */
  private static long triggerAgeInMilliseconds = 4000;

  private class Rules {
    String rulesAsText = "";
    String rulesAsDrools = "";
    Drools drools;
    /** System.currentTimeMillis() of the last load/reload of the rules from the storage */
    long reloadTimestamp;
    boolean reloadInitiated = false;
  }
  /** rules and Drools for each tenantId */
  private static Map<String,Rules> rulesMap = new HashMap<>();

  public static CirculationRulesProcessor getInstance() {
    return circulationRulesProcessor;
  }

  private CirculationRulesProcessor() {

  }


  /**
   * Set the cache time.
   * @param triggerAgeInMilliseconds  after this time the circulation rules engine is executed first for a fast reply
   *                                  and then the circulation rules get reloaded
   * @param maxAgeInMilliseconds  after this time the rules get loaded before executing the circulation rules engine
   */
  public static void setCacheTime(long triggerAgeInMilliseconds, long maxAgeInMilliseconds) {
    CirculationRulesProcessor.triggerAgeInMilliseconds = triggerAgeInMilliseconds;
    CirculationRulesProcessor.maxAgeInMilliseconds = maxAgeInMilliseconds;
  }

  /**
   * Completely drop the cache. This enforces rebuilding the drools rules
   * even when the circulation rules haven't changed.
   */
  public static void dropCache() {
    rulesMap.clear();
  }

  /**
   * Enforce reload of the tenant's circulation rules.
   * This doesn't rebuild the drools rules if the circulation rules haven't changed.
   * @param tenantId  id of the tenant
   */
  static void clearCache(String tenantId) {
    Rules rules = rulesMap.get(tenantId);
    if (rules == null) {
      return;
    }
    rules.reloadTimestamp = 0;
  }

  private boolean isCurrent(Rules rules) {
    if (rules == null) {
      return false;
    }
    return rules.reloadTimestamp + maxAgeInMilliseconds > System.currentTimeMillis();
  }

  /**
   * Reload is needed if the last reload is TRIGGER_AGE_IN_MILLISECONDS old
   * and a reload hasn't been initiated yet.
   * @param rules - rules to reload
   * @return whether reload is needed
   */
  private boolean reloadNeeded(Rules rules) {
    if (rules.reloadInitiated) {
      return false;
    }
    return rules.reloadTimestamp + triggerAgeInMilliseconds < System.currentTimeMillis();
  }

  private CompletableFuture<Result<Rules>> reloadRules(Rules rules,
    CollectionResourceClient circulationRulesClient) {

    return circulationRulesClient.get()
      .thenCompose(r -> r.after(response -> {
        JsonObject circulationRules = new JsonObject(response.getBody());

        rules.reloadTimestamp = System.currentTimeMillis();
        rules.reloadInitiated = false;

        if (log.isDebugEnabled()) {
          log.debug("circulationRules = {}", circulationRules.encodePrettily());
        }

        String rulesAsText = circulationRules.getString("rulesAsText");

        if (rulesAsText == null) {
          throw new NullPointerException("rulesAsText");
        }

        if (rules.rulesAsText.equals(rulesAsText)) {
          return ofAsync(() -> rules);
        }

        rules.rulesAsText = rulesAsText;
        rules.rulesAsDrools = Text2Drools.convert(rulesAsText);
        log.debug("rulesAsDrools = {}", rules.rulesAsDrools);
        rules.drools = new Drools(rules.rulesAsDrools);

        return ofAsync(() -> rules);
    }));
  }

  public static CirculationRuleMatch getLoanPolicyAndMatch(Drools drools, MultiMap params, Location location) {
    return drools.loanPolicy(params, location);
  }

  public static JsonArray getLoanPolicies(Drools drools, MultiMap params, Location location) {
    return drools.loanPolicies(params, location);
  }

  public static CirculationRuleMatch getLostItemPolicyAndMatch(Drools drools, MultiMap params, Location location) {
    return drools.lostItemPolicy(params, location);
  }

  public static JsonArray getLostItemPolicies(Drools drools, MultiMap params, Location location) {
    return drools.lostItemPolicies(params, location);
  }

  public static CirculationRuleMatch getNoticePolicyAndMatch(Drools drools, MultiMap params, Location location) {
    return drools.noticePolicy(params, location);
  }

  public static JsonArray getNoticePolicies(Drools drools, MultiMap params, Location location) {
    return drools.noticePolicies(params, location);
  }

  public static CirculationRuleMatch getOverduePolicyAndMatch(Drools drools, MultiMap params, Location location) {
    return drools.overduePolicy(params, location);
  }

  public static JsonArray getOverduePolicies(Drools drools, MultiMap params, Location location) {
    return drools.overduePolicies(params, location);
  }

  public static CirculationRuleMatch getRequestPolicyAndMatch(Drools drools, MultiMap params, Location location) {
    return drools.requestPolicy(params, location);
  }

  public static JsonArray getRequestPolicies(Drools drools, MultiMap params, Location location) {
    return drools.requestPolicies(params, location);
  }

  public CompletableFuture<Result<Drools>> getDrools(String tenantId,
    CollectionResourceClient circulationRulesClient) {

    CompletableFuture<Result<Drools>> cfDrools = new CompletableFuture<>();

    Rules rules = rulesMap.get(tenantId);

    if (isCurrent(rules)) {
      cfDrools.complete(succeeded(rules.drools));

      if (reloadNeeded(rules)) {
        rules.reloadInitiated = true;
        reloadRules(rules, circulationRulesClient)
          .thenCompose(r -> r.after(updatedRules -> ofAsync(() -> updatedRules.drools)));
      }

      return cfDrools;
    }

    if (rules == null) {
      rules = new Rules();
      rulesMap.put(tenantId, rules);
    }

    return reloadRules(rules, circulationRulesClient)
      .thenCompose(r -> r.after(updatedRules -> ofAsync(() -> updatedRules.drools)));
  }
}
