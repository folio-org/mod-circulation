package api.support;

import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import java.util.HashMap;

import org.folio.circulation.support.http.OkapiHeader;

import api.support.http.OkapiHeaders;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.specification.RequestSpecification;

public class RestAssuredConfiguration {
  public static RequestSpecification standardHeaders(OkapiHeaders okapiHeaders) {
    final HashMap<String, String> headers = new HashMap<>();

    headers.put(OKAPI_URL, okapiHeaders.getUrl().toString());
    headers.put(TENANT, okapiHeaders.getTenantId());
    headers.put(OkapiHeader.TOKEN, okapiHeaders.getToken());
    headers.put(OkapiHeader.REQUEST_ID, okapiHeaders.getRequestId());

    if (okapiHeaders.hasUserId()) {
      headers.put(OkapiHeader.USER_ID, okapiHeaders.getUserId());
    }

    return new RequestSpecBuilder()
      .addHeaders(headers)
      .setAccept("application/json, text/plain")
      .setContentType("application/json")
      .build();
  }

  public static RequestSpecification timeoutConfig() {
    final int defaultTimeOutInMilliseconds = 5000;

    return timeoutConfig(defaultTimeOutInMilliseconds);
  }

  public static RequestSpecification timeoutConfig(int timeOutInMilliseconds) {
    return new RequestSpecBuilder()
      .setConfig(RestAssured.config()
        .httpClient(HttpClientConfig.httpClientConfig()
          .setParam("http.connection.timeout", timeOutInMilliseconds)
          .setParam("http.socket.timeout", timeOutInMilliseconds)))
      .build();
  }
}
