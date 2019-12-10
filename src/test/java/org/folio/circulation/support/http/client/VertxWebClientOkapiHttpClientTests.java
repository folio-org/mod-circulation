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

    fakeWebServer.stubFor(get(urlEqualTo("/record"))
      .withHeader("X-Okapi-Url", equalTo(okapiUrl.toString()))
      .willReturn(okJson(new JsonObject().put("message", "hello").encodePrettily())));

    VertxWebClientOkapiHttpClient client =  createClientUsing(
      vertxAssistant.createUsingVertx(Vertx::createHttpClient), okapiUrl);

    CompletableFuture<Result<Response>> getCompleted = client.get(fakeWebServer.url("/record"));

    final Response response = getCompleted.get(5, TimeUnit.SECONDS).value();

    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getString("message"), is("hello"));
  }
}
