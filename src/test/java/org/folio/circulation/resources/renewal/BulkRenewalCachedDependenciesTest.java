package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

class BulkRenewalCachedDependenciesTest {

  @Test
  void shouldNotCallRulesEngineMoreThanOnceForSameItemTypeLoanTypeLocationPatronGroup() {
    AtomicInteger rulesEngineCalls = new AtomicInteger();
    UUID policyId = UUID.randomUUID();

    String materialTypeId = UUID.randomUUID().toString();
    String loanTypeId     = UUID.randomUUID().toString();
    String locationId     = UUID.randomUUID().toString();
    String patronGroupId  = UUID.randomUUID().toString();

    CirculationRuleMatch match = new CirculationRuleMatch(policyId.toString(),
      new AppliedRuleConditions(true, true, true));

    BulkRenewalCachedDependencies deps = new BulkRenewalCachedDependencies(
      () -> completedFuture(succeeded(TlrSettingsConfiguration.defaultSettings())),
      () -> completedFuture(succeeded(ZoneId.of("UTC"))));

    BulkRenewalCachedDependencies.RuleLookupCacheKey key =
      new BulkRenewalCachedDependencies.RuleLookupCacheKey(
        materialTypeId, loanTypeId, locationId, patronGroupId);

    Result<CirculationRuleMatch> first = deps.getCirculationRuleMatch(key, () -> {
      rulesEngineCalls.incrementAndGet();
      return completedFuture(succeeded(match));
    }).join();

    Result<CirculationRuleMatch> second = deps.getCirculationRuleMatch(key, () -> {
      rulesEngineCalls.incrementAndGet();
      return completedFuture(succeeded(match));
    }).join();

    assertEquals(1, rulesEngineCalls.get(),
      "Rules engine must be called exactly once for the same (materialType, loanType, location, patronGroup)");
    assertSame(match, first.value());
    assertSame(match, second.value());
  }

  @Test
  void shouldCallRulesEngineSeparatelyForDifferentLocations() {
    AtomicInteger rulesEngineCalls = new AtomicInteger();
    UUID policyId = UUID.randomUUID();

    String materialTypeId = UUID.randomUUID().toString();
    String loanTypeId     = UUID.randomUUID().toString();
    String patronGroupId  = UUID.randomUUID().toString();
    String locationA      = UUID.randomUUID().toString();
    String locationB      = UUID.randomUUID().toString();

    BulkRenewalCachedDependencies deps = new BulkRenewalCachedDependencies(
      () -> completedFuture(succeeded(TlrSettingsConfiguration.defaultSettings())),
      () -> completedFuture(succeeded(ZoneId.of("UTC"))));

    BulkRenewalCachedDependencies.RuleLookupCacheKey keyA =
      new BulkRenewalCachedDependencies.RuleLookupCacheKey(
        materialTypeId, loanTypeId, locationA, patronGroupId);
    BulkRenewalCachedDependencies.RuleLookupCacheKey keyB =
      new BulkRenewalCachedDependencies.RuleLookupCacheKey(
        materialTypeId, loanTypeId, locationB, patronGroupId);

    deps.getCirculationRuleMatch(keyA, () -> {
      rulesEngineCalls.incrementAndGet();
      return completedFuture(succeeded(new CirculationRuleMatch(policyId.toString(),
        new AppliedRuleConditions(true, true, true))));
    }).join();

    deps.getCirculationRuleMatch(keyB, () -> {
      rulesEngineCalls.incrementAndGet();
      return completedFuture(succeeded(new CirculationRuleMatch(policyId.toString(),
        new AppliedRuleConditions(true, true, true))));
    }).join();

    assertEquals(2, rulesEngineCalls.get(),
      "Rules engine must be called once per distinct location");
  }
}
