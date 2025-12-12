package org.folio.circulation.domain.anonymization;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.folio.circulation.domain.Request;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class RequestAnonymizationRecordsTest {

  private static Request requestWithId(String id) {
    return Request.from(new JsonObject().put("id", id));
  }

  @Test
  void withRequestsFoundCreatesNewInstanceAndKeepsOriginalUnchanged() {
    RequestAnonymizationRecords original = new RequestAnonymizationRecords();

    RequestAnonymizationRecords updated = original.withRequestsFound(
      List.of(requestWithId("r1"), requestWithId("r2"))
    );

    assertNotSame(original, updated);
    assertTrue(original.getRequestsFound().isEmpty());
    assertEquals(2, updated.getRequestsFound().size());
  }

  @Test
  void getAnonymizedRequestsFiltersFoundByAnonymizedIds() {
    Request r1 = requestWithId("r1");
    Request r2 = requestWithId("r2");

    RequestAnonymizationRecords records = new RequestAnonymizationRecords()
      .withRequestsFound(List.of(r1, r2))
      .withAnonymizedRequests(List.of("r2"));

    assertEquals(List.of("r2"), records.getAnonymizedRequestIds());
    assertEquals(1, records.getAnonymizedRequests().size());
    assertEquals("r2", records.getAnonymizedRequests().get(0).getId());
  }

  @Test
  void withNotAnonymizedRequestsStoresReasons() {
    Map<String, Set<String>> notAnon = Map.of(
      "requestNotClosed", Set.of("r1", "r2"),
      "requestNotEligibleForAnonymization", Set.of("r3")
    );

    RequestAnonymizationRecords records = new RequestAnonymizationRecords()
      .withNotAnonymizedRequests(notAnon);

    assertEquals(2, records.getNotAnonymizedRequests().size());
    assertTrue(records.getNotAnonymizedRequests().containsKey("requestNotClosed"));
  }
}
