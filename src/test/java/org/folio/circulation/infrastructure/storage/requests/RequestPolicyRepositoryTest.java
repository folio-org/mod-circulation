package org.folio.circulation.infrastructure.storage.requests;

import static org.folio.circulation.rules.ExecutableRules.MATCH_FAIL_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MaterialType;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.RequestPolicy;
import org.folio.circulation.rules.AppliedRuleConditions;
import org.folio.circulation.rules.CirculationRuleMatch;
import org.folio.circulation.rules.CirculationRulesProcessor;
import org.folio.circulation.rules.RulesExecutionParameters;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
public class RequestPolicyRepositoryTest {

  public static final String SAMPLE_POLICY_ID = UUID.randomUUID().toString();

  @Mock
  private CirculationRulesProcessor circulationRulesProcessor;
  @Mock
  private CollectionResourceClient requestPoliciesStorageClient;
  @Mock
  private Clients clients;

  private RequestPolicyRepository requestPolicyRepository;

  private Item item;
  private User user;
  private Request request;
  private RequestAndRelatedRecords requestAndRelatedRecords;

  @BeforeEach
  void setUp() {
    doReturn(requestPoliciesStorageClient).when(clients).requestPoliciesStorage();
    doReturn(circulationRulesProcessor).when(clients).circulationRulesProcessor();
    requestPolicyRepository = new RequestPolicyRepository(clients);
    item = Item.from(JsonObject.of(
      "materialType", asJson(MaterialType.unknown()),
      "loadType", asJson(LoanType.unknown()),
      "location", asJson(Location.unknown()),
      "holdings", asJson(Holdings.unknown()))
    );
    user = new User(JsonObject.of("patronGroup", "sample-group"));
    request = Request.from(JsonObject.of()).withItem(item).withRequester(user);
    requestAndRelatedRecords = new RequestAndRelatedRecords(request);
  }

  @Test
  void testLookupRequestPolicy() throws ExecutionException, InterruptedException {
    var ruleMatch = new CirculationRuleMatch(SAMPLE_POLICY_ID, mock(AppliedRuleConditions.class));
    when(circulationRulesProcessor.getRequestPolicyAndMatch(any(RulesExecutionParameters.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(ruleMatch)));

    var policy = RequestPolicy.from(JsonObject.of("id", SAMPLE_POLICY_ID));
    when(requestPoliciesStorageClient.get(SAMPLE_POLICY_ID))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(asResponse(policy))));

    var result = requestPolicyRepository.lookupRequestPolicy(requestAndRelatedRecords).get().value();

    assertEquals(SAMPLE_POLICY_ID, result.getRequestPolicy().getId());
    assertEquals(item, result.getRequest().getItem());
    assertEquals(user, result.getRequest().getUser());
    verify(circulationRulesProcessor).getRequestPolicyAndMatch(any(RulesExecutionParameters.class));
    verify(requestPoliciesStorageClient).get(SAMPLE_POLICY_ID);
  }

  @Test
  void testLookupRequestPolicyWhenItemIsNull() throws ExecutionException, InterruptedException {
    var result = requestPolicyRepository.lookupRequestPolicy(
      new RequestAndRelatedRecords(request.withItem(Item.from(null)))).get();

    assertTrue(result.failed());
    assertEquals(ServerErrorFailure.class, result.cause().getClass());
    var cause = (ServerErrorFailure) result.cause();
    assertEquals("Unable to find matching request rules for unknown item", cause.getReason());
    verifyNoInteractions(circulationRulesProcessor);
    verifyNoInteractions(requestPoliciesStorageClient);
  }

  @Test
  void testLookupRequestPolicyWhenRequestPolicyIdNotFound() throws ExecutionException, InterruptedException {
    when(circulationRulesProcessor.getRequestPolicyAndMatch(any(RulesExecutionParameters.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.failed(
        new ServerErrorFailure(MATCH_FAIL_MSG.formatted("rules", "params", "request policy")))));

    var result = requestPolicyRepository.lookupRequestPolicy(requestAndRelatedRecords).get();

    assertTrue(result.failed());
    assertEquals(ServerErrorFailure.class, result.cause().getClass());
    var cause = (ServerErrorFailure) result.cause();
    assertEquals("Unable to find matching request rules", cause.getReason());
    verify(circulationRulesProcessor).getRequestPolicyAndMatch(any(RulesExecutionParameters.class));
    verifyNoInteractions(requestPoliciesStorageClient);
  }

  @Test
  void testLookupRequestPolicyWhenExceptionThrownDuringFetchingRequestPolicyId() throws ExecutionException, InterruptedException {
    var cause = new ServerErrorFailure("Internal Server Error");
    when(circulationRulesProcessor.getRequestPolicyAndMatch(any(RulesExecutionParameters.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.failed(cause)));

    var result = requestPolicyRepository.lookupRequestPolicy(requestAndRelatedRecords).get();

    assertTrue(result.failed());
    assertEquals(cause, result.cause());
    verify(circulationRulesProcessor).getRequestPolicyAndMatch(any(RulesExecutionParameters.class));
    verifyNoInteractions(requestPoliciesStorageClient);
  }

  @Test
  void testLookupRequestPolicyWhenRequestPolicyNotFound() throws ExecutionException, InterruptedException {
    var ruleMatch = new CirculationRuleMatch(SAMPLE_POLICY_ID, mock(AppliedRuleConditions.class));
    when(circulationRulesProcessor.getRequestPolicyAndMatch(any(RulesExecutionParameters.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(ruleMatch)));

    var cause = new ServerErrorFailure("Not Found");
    when(requestPoliciesStorageClient.get(SAMPLE_POLICY_ID))
      .thenReturn(CompletableFuture.completedFuture(Result.failed(cause)));

    var result = requestPolicyRepository.lookupRequestPolicy(requestAndRelatedRecords).get();

    assertTrue(result.failed());
    assertEquals(cause, result.cause());
    verify(circulationRulesProcessor).getRequestPolicyAndMatch(any(RulesExecutionParameters.class));
    verify(requestPoliciesStorageClient).get(SAMPLE_POLICY_ID);
  }

  private static <T> JsonObject asJson(T entity) {
    return JsonObject.mapFrom(entity);
  }

  private static <T> Response asResponse(T entity) {
    return new Response(200, JsonObject.mapFrom(entity).encode(), "application/json");
  }

}
