package org.folio.circulation.rules.cache;

import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.awaitility.Awaitility.await;
import static org.folio.circulation.support.http.ContentType.APPLICATION_JSON;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.Location;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
public class CirculationRulesCacheTest {
  @Test
  void concurrencyTest() throws Exception {
    CollectionResourceClient circulationRulesClient1 = createCirculationRulesClientMock(
      "11111111-1111-1111-1111-111111111111");
    CollectionResourceClient circulationRulesClient2 = createCirculationRulesClientMock(
      "22222222-2222-2222-2222-222222222222");

    Thread thread1 = new Thread(() -> {
      CirculationRulesCache.getInstance().getDrools("tenant1", circulationRulesClient1);
      System.out.println("finished building Drools 1");
      Thread.currentThread().interrupt();
    });

    Thread thread2 = new Thread(() -> {
      CirculationRulesCache.getInstance().getDrools("tenant2", circulationRulesClient2);
      System.out.println("finished building Drools 2");
      Thread.currentThread().interrupt();
    });

    System.out.println("starting thread 1");
    thread1.start();
    System.out.println("starting thread 1");
    thread2.start();

    System.out.println("waiting for thread 1");
    await().atMost(30, TimeUnit.SECONDS).until(thread1::isInterrupted);
    System.out.println("waiting for thread 2");
    await().atMost(30, TimeUnit.SECONDS).until(thread2::isInterrupted);
    System.out.println("done waiting");

    String loanPolicyId1 = getLoanPolicyId("tenant1", circulationRulesClient1);
    String loanPolicyId2 = getLoanPolicyId("tenant2", circulationRulesClient2);

    assert !loanPolicyId1.equals(loanPolicyId2);
  }

  private String getLoanPolicyId(String tenantId, CollectionResourceClient client) throws Exception {
    return CirculationRulesCache.getInstance().getDrools(tenantId, client)
      .get().value().loanPolicy(MultiMap.caseInsensitiveMultiMap(), Location.unknown()).getPolicyId();
  }

  private CollectionResourceClient createCirculationRulesClientMock(String policyId) {
    String rulesResponse = new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("rulesAsText", format("priority: g, m, t , s, b, c, a\nfallback-policy: " +
        "l %s r %s n %s o %s i %s", policyId, policyId, policyId, policyId, policyId))
      .encodePrettily();

    CollectionResourceClient circulationRulesClient = mock(CollectionResourceClient.class);
    when(circulationRulesClient.get()).thenReturn(ofAsync(new Response(HTTP_OK, rulesResponse,
      APPLICATION_JSON)));

    return circulationRulesClient;
  }
}
