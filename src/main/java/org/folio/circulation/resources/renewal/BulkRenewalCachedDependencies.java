package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;

import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.OverdueFinePolicy;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.services.CirculationSettingsService;
import org.folio.circulation.support.results.Result;

public class BulkRenewalCachedDependencies {
  private final Supplier<CompletableFuture<Result<TlrSettingsConfiguration>>> tlrSettingsFetcher;
  private final Supplier<CompletableFuture<Result<ZoneId>>> timeZoneFetcher;
  private final ConcurrentHashMap<LoanPolicyCacheKey, CompletableFuture<Result<LoanPolicy>>> loanPolicies =
    new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, CompletableFuture<Result<OverdueFinePolicy>>> overdueFinePolicies =
    new ConcurrentHashMap<>();

  private CompletableFuture<Result<TlrSettingsConfiguration>> tlrSettings;
  private CompletableFuture<Result<ZoneId>> timeZone;

  public BulkRenewalCachedDependencies(CirculationSettingsService circulationSettingsService,
    SettingsRepository settingsRepository) {

    this(circulationSettingsService::getTlrSettings,
      settingsRepository::lookupTimeZoneSettings);
  }

  public BulkRenewalCachedDependencies(
    Supplier<CompletableFuture<Result<TlrSettingsConfiguration>>> tlrSettingsFetcher,
    Supplier<CompletableFuture<Result<ZoneId>>> timeZoneFetcher) {

    this.tlrSettingsFetcher = tlrSettingsFetcher;
    this.timeZoneFetcher = timeZoneFetcher;
  }

  public synchronized CompletableFuture<Result<TlrSettingsConfiguration>> getTlrSettings() {
    if (tlrSettings == null) {
      tlrSettings = tlrSettingsFetcher.get();
    }

    return tlrSettings;
  }

  public synchronized CompletableFuture<Result<ZoneId>> getTimeZone() {
    if (timeZone == null) {
      timeZone = timeZoneFetcher.get();
    }

    return timeZone;
  }

  public CompletableFuture<Result<LoanPolicy>> getLoanPolicy(String policyId,
    AppliedRuleConditions appliedRuleConditions,
    BiFunction<String, AppliedRuleConditions, CompletableFuture<Result<LoanPolicy>>> policyFetcher) {

    if (policyId == null) {
      return completedFuture(succeeded(LoanPolicy.unknown(null)));
    }

    return loanPolicies.computeIfAbsent(new LoanPolicyCacheKey(policyId, appliedRuleConditions),
      key -> policyFetcher.apply(key.policyId(), key.appliedRuleConditions()));
  }

  public CompletableFuture<Result<OverdueFinePolicy>> getOverdueFinePolicy(String policyId,
    Function<String, CompletableFuture<Result<OverdueFinePolicy>>> policyFetcher) {

    if (policyId == null) {
      return completedFuture(succeeded(OverdueFinePolicy.unknown(null)));
    }

    return overdueFinePolicies.computeIfAbsent(policyId, policyFetcher);
  }

  private record LoanPolicyCacheKey(String policyId, AppliedRuleConditions appliedRuleConditions) {
    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }

      if (!(object instanceof LoanPolicyCacheKey other)) {
        return false;
      }

      return Objects.equals(policyId, other.policyId)
        && sameConditions(appliedRuleConditions, other.appliedRuleConditions);
    }

    @Override
    public int hashCode() {
      return Objects.hash(policyId,
        appliedRuleConditions != null && appliedRuleConditions.isItemTypePresent(),
        appliedRuleConditions != null && appliedRuleConditions.isLoanTypePresent(),
        appliedRuleConditions != null && appliedRuleConditions.isPatronGroupPresent());
    }

    private static boolean sameConditions(AppliedRuleConditions first,
      AppliedRuleConditions second) {

      if (first == second) {
        return true;
      }

      if (first == null || second == null) {
        return false;
      }

      return first.isItemTypePresent() == second.isItemTypePresent()
        && first.isLoanTypePresent() == second.isLoanTypePresent()
        && first.isPatronGroupPresent() == second.isPatronGroupPresent();
    }
  }
}
