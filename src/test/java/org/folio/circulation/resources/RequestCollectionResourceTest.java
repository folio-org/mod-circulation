package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang.StringUtils;
import org.folio.circulation.domain.*;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestCollectionResourceTest {

  String queryWithOnePSPId = "(effectiveLocationPrimaryServicePointId==\"a9b8247d-3a7a-410e-8df6-1c5af342aa77\") sortby requestDate";
  String queryWithTwoPSPIds = "(effectiveLocationPrimaryServicePointId==(\"a9b8247d-3a7a-410e-8df6-1c5af342aa77\" or \"3a40852d-49fd-4df2-a1f9-6e2641a6e91f\")) sortby requestDate";
  String queryWithThreePSPIds = "(effectiveLocationPrimaryServicePointId==(\"a9b8247d-3a7a-410e-8df6-1c5af342aa77\" or \"3a40852d-49fd-4df2-a1f9-6e2641a6e91f\" or \"c4c90014-c8c9-4ade-8f24-b5e313319f4b\")) sortby requestDate";
  String queryWithNoPSPIds = "() sortby requestDate";

  RequestCollectionResource requestCollectionResource = new RequestCollectionResource(mock(HttpClient.class));

  @Test
  void parseQueryWithOnePSPId() {
    List<String> tokens = requestCollectionResource.parseEffectiveLocationIds(queryWithOnePSPId);
    assertEquals(1, tokens.size());
    assertEquals("a9b8247d-3a7a-410e-8df6-1c5af342aa77", tokens.get(0));
  }

  @Test
  void parseQueryWithTwoPSPIds() {
    List<String> tokens = requestCollectionResource.parseEffectiveLocationIds(queryWithTwoPSPIds);
    assertEquals(2, tokens.size());
    assertEquals("a9b8247d-3a7a-410e-8df6-1c5af342aa77", tokens.get(0));
    assertEquals("3a40852d-49fd-4df2-a1f9-6e2641a6e91f", tokens.get(1));
  }

  @Test
  void parseQueryWithThreePSPIds() {
    List<String> tokens = requestCollectionResource.parseEffectiveLocationIds(queryWithThreePSPIds);
    assertEquals(3, tokens.size());
    assertEquals("a9b8247d-3a7a-410e-8df6-1c5af342aa77", tokens.get(0));
    assertEquals("3a40852d-49fd-4df2-a1f9-6e2641a6e91f", tokens.get(1));
    assertEquals("c4c90014-c8c9-4ade-8f24-b5e313319f4b", tokens.get(2));
  }

  @Test
  void parseQueryWithNoPSPIds() {
    List<String> tokens = requestCollectionResource.parseEffectiveLocationIds(queryWithNoPSPIds);
    assertEquals(0, tokens.size());
  }

  @Test
  void mapToJson_matched() {
    UUID primaryServicePointId = UUID.randomUUID();
    Request request = mock(Request.class);
    when(request.asJson()).thenReturn(new JsonObject());
    Item item = mock(Item.class);
    Institution institution = new Institution(StringUtils.EMPTY, StringUtils.EMPTY);
    Campus campus = new Campus(StringUtils.EMPTY, StringUtils.EMPTY);
    Library library = new Library(StringUtils.EMPTY, StringUtils.EMPTY);
    ServicePoint primaryServicePoint = ServicePoint.unknown(primaryServicePointId.toString(), StringUtils.EMPTY);
    Location location = new Location("11", null, null, null, List.of(UUID.randomUUID()), primaryServicePointId, institution, campus, library, primaryServicePoint);
    when(item.getLocation()).thenReturn(location);
    when(request.getItem()).thenReturn(item);

    MultipleRecords<Request> requests = new MultipleRecords<>(List.of(request), 1);
    final List<String> effectiveLocationIds = List.of(primaryServicePointId.toString());

    JsonObject result = requestCollectionResource.mapToJson(requests, effectiveLocationIds);
    assertEquals(2, result.getMap().size());
    assertEquals(1, result.getMap().get("totalRecords"));
  }

  @Test
  void mapToJson_mismatched() {
    UUID primaryServicePointId = UUID.randomUUID();
    Request request = mock(Request.class);
    Item item = mock(Item.class);
    Institution institution = new Institution(StringUtils.EMPTY, StringUtils.EMPTY);
    Campus campus = new Campus(StringUtils.EMPTY, StringUtils.EMPTY);
    Library library = new Library(StringUtils.EMPTY, StringUtils.EMPTY);
    ServicePoint primaryServicePoint = ServicePoint.unknown(primaryServicePointId.toString(), StringUtils.EMPTY);
    Location location = new Location("11", null, null, null, List.of(UUID.randomUUID()), primaryServicePointId, institution, campus, library, primaryServicePoint);
    when(item.getLocation()).thenReturn(location);
    when(request.getItem()).thenReturn(item);

    MultipleRecords<Request> requests = new MultipleRecords<>(List.of(request), 1);
    final List<String> effectiveLocationIds = List.of("random id");

    JsonObject result = requestCollectionResource.mapToJson(requests, effectiveLocationIds);
    assertEquals(2, result.getMap().size());
    assertEquals(0, result.getMap().get("totalRecords"));
  }
}
