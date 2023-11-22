package org.folio.circulation.rules.cache;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.events.DomainEvent;
import org.folio.circulation.domain.events.EntityChangedEventData;
import org.folio.circulation.rules.Drools;
import org.folio.circulation.rules.ExecutableRules;
import org.folio.circulation.rules.Text2Drools;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public final class CirculationRulesCache {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final CirculationRulesCache instance = new CirculationRulesCache();
  /** rules and Drools for each tenantId */
  private final Map<String, Rules> rulesMap = new ConcurrentHashMap<>();

  public static CirculationRulesCache getInstance() {
    return instance;
  }

  private CirculationRulesCache() {}

  public void dropCache() {
    rulesMap.clear();
  }

  public CompletableFuture<Result<Drools>> reloadRules(String tenantId,
    CollectionResourceClient circulationRulesClient) {

    log.info("reloadRules:: reloading rules for tenant {}", tenantId);

    return circulationRulesClient.get()
      .thenApply(r -> r.map(response -> getRulesAsText(response, tenantId)))
      .thenApply(r -> r.next(rulesAsText -> buildRules(tenantId, rulesAsText)));
  }

  private static String getRulesAsText(Response response, String tenantId) {
    log.debug("getRulesAsText:: parameters tenantId: {}", tenantId);
    final var circulationRules = new JsonObject(response.getBody());
    log.debug("getRulesAsText:: circulationRules: {}", circulationRules::encodePrettily);

    return circulationRules.getString("rulesAsText");
  }

  public Result<Drools> buildRules(String tenantId, String rulesAsText) {
    log.info("buildRules:: building rules for tenant {}", tenantId);
    log.debug("buildRules:: rules={}", rulesAsText);

    if (isBlank(rulesAsText)) {
      log.warn("buildRules:: rules are blank for tenant {}", tenantId);
      return failed(new ServerErrorFailure("Cannot apply blank circulation rules"));
    }

    String droolsText = Text2Drools.convert(rulesAsText);
    Drools drools = new Drools(tenantId, droolsText);
    log.info("buildRules:: done building Drools for tenant {}", tenantId);
    log.debug("buildRules:: Drools as text: {}", droolsText);

    long timestamp = System.currentTimeMillis();
    log.debug("buildRules:: timestamp={}", timestamp);
    Rules rules = new Rules(rulesAsText, droolsText, drools, timestamp);
    rulesMap.put(tenantId, rules);

    return succeeded(drools);
  }

  public CompletableFuture<Result<ExecutableRules>> getExecutableRules(String tenantId,
    CollectionResourceClient circulationRulesClient) {

    return getDrools(tenantId, circulationRulesClient)
      .thenApply(r -> r.map(drools ->
        new ExecutableRules(rulesMap.get(tenantId).getRulesAsText(), drools)));
  }

  public CompletableFuture<Result<Drools>> getDrools(String tenantId,
    CollectionResourceClient circulationRulesClient) {

    log.info("getDrools:: getting Drools for tenant {}", tenantId);

    return Optional.ofNullable(getRules(tenantId))
      .map(Rules::getDrools)
      .map(Result::ofAsync)
      .orElseGet(() -> reloadRules(tenantId, circulationRulesClient));
  }

  public void handleRulesUpdateEvent(DomainEvent<EntityChangedEventData> event) {
    log.debug("handleRulesUpdateEvent:: event={}", () -> event);

    final String tenantId = event.tenantId();
    log.info("handleRulesUpdateEvent:: handling rules update event {} for tenant {}",
      event.id(), event.tenantId());
    final Rules cachedRules = getRules(tenantId);

    if (cachedRules == null) {
      // if cache is empty, rules are downloaded from storage anyway when they are first requested
      log.info("handleRulesUpdateEvent:: no cached rules for tenant {}, ignoring event {}",
        tenantId, event.id());
      return;
    }

    final long eventTimestamp = event.timestamp();
    final long cacheTimestamp = cachedRules.getReloadTimestamp();
    if (eventTimestamp < cacheTimestamp) {
      log.info("handleRulesUpdateEvent:: ignoring event {}: event timestamp is {}, " +
          "cache timestamp is {}", event.id(), eventTimestamp, cacheTimestamp);
      return;
    }

    buildRules(tenantId, event.data().newVersion().getString("rulesAsText"));
  }

  public Rules getRules(String tenantId) {
    final Rules cachedRules = rulesMap.get(tenantId);
    if (cachedRules == null) {
      log.info("getRulesFromCache:: cache miss for tenant {}", tenantId);
    } else {
      log.info("getRulesFromCache:: cache hit for tenant {}", tenantId);
      log.debug("getRulesFromCache:: cached rules: {}", cachedRules::getRulesAsText);
    }

    return cachedRules;
  }
}
