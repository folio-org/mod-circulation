package org.folio.circulation.support.utils;

import static org.folio.circulation.support.utils.LogUtil.asJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.circulation.domain.Loan;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class LogUtilTest {
  @Test
  public void asJsonWithObjectShouldReturnNullIfCallWithNull() {
    assertNull(asJson((Object) null));
  }

  @Test
  public void asJsonShouldReturnStringValueOfJson() {
    JsonObject json = new JsonObject()
      .put("key", "value");
    assertEquals("{\"key\":\"value\"}", asJson(json));
  }

  @Test
  public void asJsonWithJsonObjectShouldReturnNullIfException() {
    JsonObject jsonObject = mock(JsonObject.class);
    when(jsonObject.encode()).thenThrow(new RuntimeException("Test Exception"));

    assertNull(asJson(jsonObject));
    verify(jsonObject, times(1)).encode();
  }

  @Test
  public void asJsonWithListShouldReturnNullIfCallWithNull() {
    assertNull(asJson((Object) null));
  }

  @Test
  public void asJsonWithJsonObjectShouldReturnNullIfJsonMappingException() {
    assertNull(asJson(Loan.from(new JsonObject())));
  }

  @Test
  public void asJsonWithListOfJsonObjectsShouldReturnStringValue() {
    String result = asJson(List.of(
      new JsonObject().put("test", "one"),
      new JsonObject().put("test", "two"),
      new JsonObject().put("test", "three")), 2);

    assertEquals("list(size: 3, first 2 elements: [{\"test\":\"one\"}, {\"test\":\"two\"}])", result);
  }

  @Test
  public void asJsonWithListOfJsonObjectsShouldReturnFirstTenElements() {
    String result = asJson(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"));

    assertEquals("list(size: 11, first 10 elements: [\"1\", \"2\", \"3\", \"4\", \"5\", \"6\", \"7\", \"8\", \"9\", \"10\"])", result);
  }

  @Test
  public void asJsonWithListOfJsonObjectsShouldReturnNullIfException() {
    List<?> list = mock(List.class);
    when(list.size()).thenThrow(new RuntimeException("Test Exception"));
    String result = asJson(list, 3);

    assertNull(result);
    verify(list, times(1)).size();
  }

  @Test
  public void asJsonWithListOfStringObjectsShouldReturnStringValue() {
    String result = asJson(List.of("one", "two", "three"), 2);

    assertEquals("list(size: 3, first 2 elements: [\"one\", \"two\"])", result);
  }

  @Test
  public void asJsonShouldReturnNullIfCallWithNullAndSizeValue() {
    assertNull(asJson(null, 10));
  }

  @Test
  public void headersAsStringShouldRemoveOkapiTokenAndReturnRepresentation() {
    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put("X-Okapi-Tenant", "testTenant");
    okapiHeaders.put("X-Okapi-Token", "token");
    okapiHeaders.put("X-Okapi-Url", "url");
    assertEquals("{x-okapi-tenant=testTenant, x-okapi-url=url}", LogUtil.headersAsString(okapiHeaders));
  }

  @Test
  public void headersAsStringShouldReturnNullIfHeadersNull() {
    assertNull(LogUtil.headersAsString(null));
  }
}
