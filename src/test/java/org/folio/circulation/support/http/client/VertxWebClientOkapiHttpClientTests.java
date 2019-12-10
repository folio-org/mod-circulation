package org.folio.circulation.support.http.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.folio.circulation.support.http.client.VertxWebClientOkapiHttpClient.createClientUsing;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.VertxAssistant;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class VertxWebClientOkapiHttpClientTests {
  private static VertxAssistant vertxAssistant;

  @Rule
  public WireMockRule fakeWebServer = new WireMockRule(8081);

  @BeforeClass
  public static void beforeAll() {
    vertxAssistant = new VertxAssistant();

    vertxAssistant.start();
  }

  @AfterClass
  public static void afterAll() {
    if (vertxAssistant != null) {
      vertxAssistant.stop();
    }
  }

  @Test
  public void canGetJson() throws InterruptedException, ExecutionException,
    TimeoutException, MalformedURLException {

    final URL okapiUrl = new URL("http://okapi.com");
    final String tenantId = "test-tenant";
    final String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkaWt1X2FkbWluIiwidXNlcl9pZCI6ImFhMjZjYjg4LTc2YjEtNTQ1OS1hMjM1LWZjYTRmZDI3MGMyMyIsImlhdCI6MTU3NjAxMzY3MiwidGVuYW50IjoiZGlrdSJ9.oGCb0gDIdkXGlCiECvJHgQMXD3QKKW2vTh7PPCrpds8";
    final String userId = "aa26cb88-76b1-5459-a235-fca4fd270c23";
    final String requestId = "test-request-id";

    final String locationResponseHeader = "/a-different-location";

    fakeWebServer.stubFor(get(urlEqualTo("/record"))
      .withHeader("X-Okapi-Url", equalTo(okapiUrl.toString()))
      .withHeader("X-Okapi-Tenant", equalTo(tenantId))
      .withHeader("X-Okapi-Token", equalTo(token))
      .withHeader("X-Okapi-User-Id", equalTo(userId))
      .withHeader("X-Okapi-Request-Id", equalTo(requestId))
      .willReturn(okJson(new JsonObject().put("message", "hello").encodePrettily())
        .withHeader("Location", locationResponseHeader)));

    VertxWebClientOkapiHttpClient client =  createClientUsing(
      vertxAssistant.createUsingVertx(Vertx::createHttpClient), okapiUrl,
      tenantId, token, userId, requestId);

    CompletableFuture<Result<Response>> getCompleted = client.get(fakeWebServer.url("/record"));

    final Response response = getCompleted.get(5, TimeUnit.SECONDS).value();

    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getString("message"), is("hello"));
    assertThat(response.getContentType(), is("application/json"));
    assertThat(response.getHeader("location"), is(locationResponseHeader));
  }
}
