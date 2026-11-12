package org.folio.circulation.infrastructure.storage.loans;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.CirculationRulesProcessor;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class LoanPolicyRepositoryTest {
  private static final UUID POLICY_ID = UUID.randomUUID();

  @Mock
  private Clients clients;
  @Mock
  private CollectionResourceClient policyStorageClient;
  @Mock
  private CirculationRulesProcessor circulationRulesProcessor;
  @Mock
  private Item item;
  @Mock
  private User user;

  private LoanPolicyRepository repository;

  @BeforeEach
  void setUp() {
    when(clients.loanPoliciesStorage()).thenReturn(policyStorageClient);
    when(clients.circulationRulesProcessor()).thenReturn(circulationRulesProcessor);
    repository = new LoanPolicyRepository(clients);
  }

  @ParameterizedTest
  @CsvSource({
    ", , ",
    "false, , ",
    "true, 5, 2",
    "true, 0, 100",
    "true, 100, 0"
  })
  void readsAutomaticRenewalSettingsById(Boolean automaticRenewal,
    Integer firstAttemptDays, Integer retryPeriodDays)
    throws ExecutionException, InterruptedException {

    stubStorageResponse(automaticRenewal, firstAttemptDays, retryPeriodDays);

    var result = repository.getLoanPolicyById(POLICY_ID.toString()).get();

    assertTrue(result.succeeded());
    assertAutomaticRenewalSettings(result.value(), automaticRenewal,
      firstAttemptDays, retryPeriodDays);
    verify(policyStorageClient).get(POLICY_ID.toString());
  }

  @ParameterizedTest
  @CsvSource({
    ", , ",
    "false, , ",
    "true, 5, 2",
    "true, 0, 100",
    "true, 100, 0"
  })
  void readsAutomaticRenewalSettingsThroughCirculationRules(Boolean automaticRenewal,
    Integer firstAttemptDays, Integer retryPeriodDays)
    throws ExecutionException, InterruptedException {

    stubStorageResponse(automaticRenewal, firstAttemptDays, retryPeriodDays);
    when(item.getEffectiveLocationId()).thenReturn(UUID.randomUUID().toString());
    when(item.getLoanTypeId()).thenReturn(UUID.randomUUID().toString());
    when(item.getMaterialTypeId()).thenReturn(UUID.randomUUID().toString());
    when(user.getPatronGroupId()).thenReturn(UUID.randomUUID().toString());
    var ruleMatch = new CirculationRuleMatch(POLICY_ID.toString(),
      new AppliedRuleConditions(false, false, false));
    when(circulationRulesProcessor.getLoanPolicyAndMatch(any(RulesExecutionParameters.class)))
      .thenReturn(completedFuture(succeeded(ruleMatch)));
    var loan = Loan.from(new JsonObject()).withItem(item).withUser(user);

    var result = repository.lookupPolicy(loan).get();

    assertTrue(result.succeeded());
    assertAutomaticRenewalSettings(result.value(), automaticRenewal,
      firstAttemptDays, retryPeriodDays);
    verify(circulationRulesProcessor)
      .getLoanPolicyAndMatch(any(RulesExecutionParameters.class));
    verify(policyStorageClient).get(POLICY_ID.toString());
  }

  private void stubStorageResponse(Boolean automaticRenewal,
    Integer firstAttemptDays, Integer retryPeriodDays) {

    var representation = new LoanPolicyBuilder()
      .withId(POLICY_ID)
      .withAutomaticRenewal(automaticRenewal)
      .withFirstAutomaticRenewalAttemptDaysBeforeDueDate(firstAttemptDays)
      .withAutomaticRenewalRetryPeriodInDays(retryPeriodDays)
      .create();
    var response = new Response(200, representation.encode(), "application/json");
    when(policyStorageClient.get(POLICY_ID.toString()))
      .thenReturn(completedFuture(succeeded(response)));
  }

  private void assertAutomaticRenewalSettings(LoanPolicy policy,
    Boolean automaticRenewal, Integer firstAttemptDays, Integer retryPeriodDays) {

    assertEquals(POLICY_ID.toString(), policy.getId());
    assertEquals(Boolean.TRUE.equals(automaticRenewal),
      policy.isAutomaticRenewalEnabled());
    assertEquals(firstAttemptDays,
      policy.getFirstAutomaticRenewalAttemptDaysBeforeDueDate());
    assertEquals(retryPeriodDays, policy.getAutomaticRenewalRetryPeriodInDays());
  }
}
