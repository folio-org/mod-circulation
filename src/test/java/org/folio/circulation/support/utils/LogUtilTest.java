package org.folio.circulation.support.utils;

import static org.folio.circulation.support.utils.LogUtil.asJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class LogUtilTest {
  @Test
  void asJsonWithObjectShouldReturnNullIfCallWithNull() {
    assertNull(asJson((Object) null));
  }

  @Test
  void asJsonShouldReturnStringValueOfJson() {
    JsonObject json = new JsonObject()
      .put("key", "value");
    assertEquals("{\"key\":\"value\"}", asJson(json));
  }

  @Test
  void cropShouldReturnNullIfException() {
    String result = LogUtil.crop(null);
    assertNull(result);
  }

  @Test
  void asJsonWithJsonObjectShouldReturnNullIfException() {
    JsonObject jsonObject = mock(JsonObject.class);
    when(jsonObject.encode()).thenThrow(new RuntimeException("Test Exception"));

    assertNull(asJson(jsonObject));
    verify(jsonObject, times(1)).encode();
  }

  @Test
  void asJsonWithListShouldReturnNullIfCallWithNull() {
    assertNull(asJson((Object) null));
  }

  @Test
  void asJsonWithJsonObjectShouldReturnNullIfJsonMappingException() {
    assertNull(asJson(Loan.from(new JsonObject())));
  }

  @Test
  void asJsonWithListOfJsonObjectsShouldReturnTruncatedStringValue() {
    String result = asJson(List.of(
      new JsonObject().put("test", "one"),
      new JsonObject().put("test", "two"),
      new JsonObject().put("test", "three")), 2);

    assertEquals("list(size: 3, first 2 elements: [{\"test\":\"one\"}, {\"test\":\"two\"}])", result);
  }

  @Test
  void asJsonWithListOfJsonObjectsShouldReturnStringValue() {
    String result = asJson(List.of(
      new JsonObject().put("test", "one"),
      new JsonObject().put("test", "two"),
      new JsonObject().put("test", "three")), 10);

    assertEquals("list(size: 3, elements: [{\"test\":\"one\"}, {\"test\":\"two\"}, {\"test\":\"three\"}])", result);
  }

  @Test
  void asJsonWithListOfJsonObjectsShouldReturnFirstTenElements() {
    String result = asJson(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"));

    assertEquals("list(size: 11, first 10 elements: [\"1\", \"2\", \"3\", \"4\", \"5\", \"6\", \"7\", \"8\", \"9\", \"10\"])", result);
  }

  @Test
  void asJsonWithListOfJsonObjectsShouldReturnNullIfException() {
    List<?> list = mock(List.class);
    when(list.size()).thenThrow(new RuntimeException("Test Exception"));
    String result = asJson(list, 3);

    assertNull(result);
    verify(list, times(1)).size();
  }

  @Test
  void asJsonWithListOfStringObjectsShouldReturnTruncatedStringValue() {
    String result = asJson(List.of("one", "two", "three"), 2);

    assertEquals("list(size: 3, first 2 elements: [\"one\", \"two\"])", result);
  }

  @Test
  void asJsonShouldReturnNullIfCallWithNullAndSizeValue() {
    assertNull(asJson(null, 10));
  }

  @Test
  void headersAsStringShouldRemoveOkapiTokenAndReturnRepresentation() {
    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put("X-Okapi-Tenant", "testTenant");
    okapiHeaders.put("X-Okapi-Token", "token");
    okapiHeaders.put("X-Okapi-Url", "url");
    assertEquals("{x-okapi-tenant=testTenant, x-okapi-url=url}", LogUtil.headersAsString(okapiHeaders));
  }

  @Test
  void headersAsStringShouldReturnNullIfHeadersNull() {
    assertNull(LogUtil.headersAsString(null));
  }

  @Test
  void listAsStringHandlesNullValue() {
    assertEquals("null", LogUtil.listAsString(null));
  }

  @Test
  void listAsStringHandlesEmptyList() {
    assertEquals("list(size=0)", LogUtil.listAsString(List.of()));
  }

  @Test
  void listAsStringHandlesNonEmptyList() {
    assertEquals("list(size=2)", LogUtil.listAsString(List.of("1", "2")));
  }

  @Test
  void collectionAsStringHandlesNullValue() {
    assertEquals("null", LogUtil.collectionAsString(null));
  }

  @Test
  void collectionAsStringHandlesEmptyCollection() {
    assertEquals("collection(size=0)", LogUtil.collectionAsString(List.of()));
  }

  @Test
  void collectionAsStringHandlesNonEmptyCollection() {
    assertEquals("collection(size=2)", LogUtil.collectionAsString(List.of("1", "2")));
  }

  @Test
  void mapAsStringHandlesNullValue() {
    assertEquals("null", LogUtil.mapAsString(null));
  }

  @Test
  void mapAsStringHandlesEmptyMap() {
    assertEquals("map(size=0)", LogUtil.mapAsString(Map.of()));
  }

  @Test
  void mapAsStringHandlesNonEmptyMap() {
    assertEquals("map(size=2)", LogUtil.mapAsString(Map.of("key1", "value2", "key2", "value2")));
  }

  @Test
  void multipleRecordsAsStringHandlesNullValue() {
    assertEquals("null", LogUtil.multipleRecordsAsString(null));
  }

  @Test
  void multipleRecordsAsStringHandlesEmptyMultipleRecords() {
    assertEquals("records(size=0)", LogUtil.multipleRecordsAsString(MultipleRecords.empty()));
  }

  @Test
  void multipleRecordsAsStringHandlesNonEmptyMultipleRecords() {
    MultipleRecords<String> multipleRecords = new MultipleRecords<>(List.of("1", "2"), 2);
    assertEquals("records(size=2)", LogUtil.multipleRecordsAsString(multipleRecords));
  }

  @Test
  void resultAsStringHandlesNullValue() {
    assertEquals("null", LogUtil.resultAsString(null));
  }

  @Test
  void resultAsStringHandlesFailedResult() {
    assertEquals("result(failed)", LogUtil.resultAsString(Result.failed(null)));
  }

  @Test
  void resultAsStringHandlesSucceededResult() {
    assertEquals("result(abc)", LogUtil.resultAsString(Result.succeeded("abc")));
  }
}
