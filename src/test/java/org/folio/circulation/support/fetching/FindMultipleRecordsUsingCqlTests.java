package org.folio.circulation.support.fetching;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.fetching.RecordFetching.findWithCqlQuery;
import static org.folio.circulation.support.http.client.PageLimit.noLimit;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.FindWithCqlQuery;
import org.folio.circulation.support.GetManyRecordsClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class FindMultipleRecordsUsingCqlTests {
  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  void shouldFetchRecordsInSinglePage() {
    final GetManyRecordsClient client = clientThatAlwaysReturnsCannedResponse();

    final FindWithCqlQuery<JsonObject> fetcher = findWithCqlQuery(
      client, "records", identity());

    final Result<CqlQuery> query = CqlQuery.exactMatch("Status", "Open");

    fetcher.findByQuery(query);

    verify(client).getMany(eq(query.value()), eq(noLimit()));
  }

  private GetManyRecordsClient clientThatAlwaysReturnsCannedResponse() {
    final GetManyRecordsClient mock = mock(GetManyRecordsClient.class);

    when(mock.getMany(any(), any())).thenReturn(cannedResponse());

    return mock;
  }

  private CompletableFuture<Result<Response>> cannedResponse() {
    return CompletableFuture.completedFuture(Result.of(
      () -> {
        final JsonObject body = new JsonObject();

        body.put("records", new JsonArray());

        return new Response(200, body.encodePrettily(), "application/json");
      }));
  }
}
