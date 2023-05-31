package org.folio.circulation.rules.cache;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
  /** rules and Drools for each tenantId */
  private Map<String, Rules> rulesMap = new ConcurrentHashMap<>();

  public static CirculationRulesCache getInstance() {
    return instance;
  }

  private CirculationRulesCache() {}

  public void dropCache() {
    rulesMap.clear();
  }

  public void dropRulesForTenant(String tenantId) {
    Rules rules = new Rules();
    rulesMap.put(tenantId, rules);
  }

  private boolean rulesExist(String tenantId) {
    if (rulesMap.containsKey(tenantId)) {
      Rules rules = rulesMap.get(tenantId);
      if (rules != null) {
        if (rules.getRulesAsText() != "") {
          return true;
        }
      }
    }
    return false;
  }

  public CompletableFuture<Result<Rules>> reloadRules(String tenantId,
    CollectionResourceClient circulationRulesClient) {
    log.info("Reloading rules for tenant {}", tenantId);

    return circulationRulesClient.get()
      .thenCompose(r -> r.after(response -> {
        log.info("Fetched rules for tenant {}", tenantId);
        JsonObject circulationRules = new JsonObject(response.getBody());

        if (log.isInfoEnabled()) {
          log.info("circulationRules = {}", circulationRules.encodePrettily());
        }

        String rulesAsText = circulationRules.getString("rulesAsText");

        if (isBlank(rulesAsText)) {
          log.info("Rules text is blank for tenant {}", tenantId);
          return completedFuture(failed(new ServerErrorFailure(
            "Cannot apply blank circulation rules")));
        }

        String droolsText = Text2Drools.convert(rulesAsText);
        Drools drools =  new Drools(tenantId, droolsText);
        log.info("rulesAsDrools = {}", droolsText);

        Rules rules = new Rules(rulesAsText, droolsText, drools, System.currentTimeMillis());
        log.info("Done building Drools object for tenant {}", tenantId);
        rulesMap.put(tenantId, rules);
        return ofAsync(() -> rules);
      }));
  }

  public CompletableFuture<Result<ExecutableRules>> getExecutableRules(String tenantId,
    CollectionResourceClient circulationRulesClient) {

    return getDrools(tenantId, circulationRulesClient)
      .thenApply(r -> r.map(drools ->
        new ExecutableRules(rulesMap.get(tenantId).getRulesAsText(), drools)));
  }

  public CompletableFuture<Result<Drools>> getDrools(String tenantId,
    CollectionResourceClient circulationRulesClient) {

    log.info("Getting Drools for tenant {}", tenantId);

    final CompletableFuture<Result<Drools>> cfDrools = new CompletableFuture<>();

    if (rulesExist(tenantId)) {
      Rules rules = rulesMap.get(tenantId);
      DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
      String strDate = dateFormat.format(rules.getReloadTimestamp()); 
      log.info("Rules object found, last updated: " + strDate);
      cfDrools.complete(succeeded(rules.getDrools()));
      return cfDrools;
    }

    log.info("Circulation rules have not been loaded, initializing");

    return reloadRules(tenantId, circulationRulesClient)
      .thenCompose(r -> r.after(updatedRules -> ofAsync(() -> updatedRules.getDrools())));
  }
}
