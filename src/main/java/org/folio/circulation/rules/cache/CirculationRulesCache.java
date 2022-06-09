package org.folio.circulation.rules.cache;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.folio.circulation.rules.Drools;
import org.folio.circulation.rules.ExecutableRules;
import org.folio.circulation.rules.Text2Drools;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;


import io.vertx.core.json.JsonObject;

public final class CirculationRulesCache {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final CirculationRulesCache instance = new CirculationRulesCache();
  /** after this time the rules get loaded before executing the circulation rules engine */
  private static final long MAX_AGE_IN_MILLISECONDS = 5000;
  /** after this time the circulation rules engine is executed first for a fast reply
   * and then the circulation rules get reloaded */
  private static final long TRIGGER_AGE_IN_MILLISECONDS = 4000;
  /** rules and Drools for each tenantId */
  private final Map<String, Rules> rulesMap = new ConcurrentHashMap<>();

  public static CirculationRulesCache getInstance() {
    return instance;
  }

  private CirculationRulesCache() {}

  /**
   * Completely drop the cache. This enforces rebuilding the drools rules
   * even when the circulation rules haven't changed.
   */
  public void dropCache() {
    rulesMap.clear();
  }

  /**
   * Enforce reload of the tenant's circulation rules.
   * This doesn't rebuild the drools rules if the circulation rules haven't changed.
   * @param tenantId  id of the tenant
   */
  public void clearCache(String tenantId) {
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
    return rules.reloadTimestamp + MAX_AGE_IN_MILLISECONDS > System.currentTimeMillis();
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
    return rules.reloadTimestamp + TRIGGER_AGE_IN_MILLISECONDS < System.currentTimeMillis();
  }

  private CompletableFuture<Result<Rules>> reloadRules(Rules rules,
    CollectionResourceClient circulationRulesClient) {

    return circulationRulesClient.get()
      .thenCompose(r -> r.after(response -> {
        JsonObject circulationRules = new JsonObject(response.getBody());

        rules.reloadTimestamp = System.currentTimeMillis();
        rules.reloadInitiated = false;

        if (log.isInfoEnabled()) {
          log.info("circulationRules = {}", circulationRules.encodePrettily());
        }

        String rulesAsText = circulationRules.getString("rulesAsText");

        if (isBlank(rulesAsText)) {
          return completedFuture(failed(new ServerErrorFailure(
            "Cannot apply blank circulation rules")));
        }

        if (rules.rulesAsText.equals(rulesAsText)) {
          return ofAsync(() -> rules);
        }

        rules.rulesAsText = rulesAsText;
        rules.rulesAsDrools = Text2Drools.convert(rulesAsText);

        log.info("rulesAsDrools = {}", rules.rulesAsDrools);
        rules.drools = new Drools(rules.rulesAsDrools);

        return ofAsync(() -> rules);
      }));
  }

  public CompletableFuture<Result<ExecutableRules>> getExecutableRules(String tenantId,
    CollectionResourceClient circulationRulesClient) {

    return getDrools(tenantId, circulationRulesClient)
      .thenApply(r -> r.map(drools ->
        new ExecutableRules(rulesMap.get(tenantId).rulesAsText, drools)));
  }

  public CompletableFuture<Result<Drools>> getDrools(String tenantId,
    CollectionResourceClient circulationRulesClient) {

    final CompletableFuture<Result<Drools>> cfDrools = new CompletableFuture<>();
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

  private class Rules {
    private volatile String rulesAsText = "";
    private volatile String rulesAsDrools = "";
    private volatile Drools drools;
    /** System.currentTimeMillis() of the last load/reload of the rules from the storage */
    private volatile long reloadTimestamp;
    private volatile boolean reloadInitiated = false;
  }
}
