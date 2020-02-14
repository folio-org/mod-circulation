package org.folio.circulation.support;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.PageLimit.maximumLimit;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@RunWith(MockitoJUnitRunner.class)
public class FindMultipleRecordsByIdTests {
  final int MAX_VALUES_PER_CQL_SEARCH_QUERY = 50;

  @Test
  public void canFetchSingleBatchOfRecordsById() {
    final GetManyRecordsClient client = clientThatAlwaysReturnsCannedResponse();

    final MultipleRecordFetcher<JsonObject> fetcher = new MultipleRecordFetcher<>(
      client, "records", identity(), MAX_VALUES_PER_CQL_SEARCH_QUERY);

    final List<String> ids = generateIds(MAX_VALUES_PER_CQL_SEARCH_QUERY);

    fetcher.findByIds(ids);

    ArgumentCaptor<CqlQuery> generatedCqlQuery = ArgumentCaptor.forClass(CqlQuery.class);

    verify(client).getMany(generatedCqlQuery.capture(), eq(maximumLimit()));

    final CqlQuery expectedQuery = exactMatchAny("id", ids).value();

    assertThat(generatedCqlQuery.getValue(), is(expectedQuery));
  }

  @Test
  public void canFetchMultiplesBatchesOfRecordsById() {
    final GetManyRecordsClient client = clientThatAlwaysReturnsCannedResponse();

    final MultipleRecordFetcher<JsonObject> fetcher = new MultipleRecordFetcher<>(
      client, "records", identity());

    final List<String> firstSetOfIds = generateIds(MAX_VALUES_PER_CQL_SEARCH_QUERY);
    final List<String> secondSetOfIds = generateIds(MAX_VALUES_PER_CQL_SEARCH_QUERY);

    fetcher.findByIds(combineSetsOfIds(firstSetOfIds, secondSetOfIds));

    ArgumentCaptor<CqlQuery> generatedCqlQuery = ArgumentCaptor.forClass(CqlQuery.class);

    verify(client, times(2))
      .getMany(generatedCqlQuery.capture(), eq(maximumLimit()));

    final CqlQuery firstExpectedQuery = exactMatchAny("id", firstSetOfIds).value();
    final CqlQuery secondExpectedQuery = exactMatchAny("id", secondSetOfIds).value();

    assertThat(generatedCqlQuery.getAllValues().get(0), is(firstExpectedQuery));
    assertThat(generatedCqlQuery.getAllValues().get(1), is(secondExpectedQuery));
  }

  @Test
  public void SearchingForNoIdsAssumesNoRecordsWillBeFound() throws InterruptedException,
      ExecutionException, TimeoutException {

    final GetManyRecordsClient client = clientThatAlwaysReturnsCannedResponse();

    final MultipleRecordFetcher<JsonObject> fetcher = new MultipleRecordFetcher<>(
      client, "records", identity());

    final CompletableFuture<Result<MultipleRecords<JsonObject>>> futureResult
      = fetcher.findByIds(new ArrayList<>());

    final MultipleRecords<JsonObject> result = futureResult.get(1, SECONDS).value();

    assertThat("Should assume no records are found",
        result.isEmpty(), is(true));

    verify(client, times(0)).getMany(any(), any());
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

  private List<String> generateIds(int size) {
    return Stream.generate(UUID::randomUUID)
      .map(UUID::toString)
      .limit(size).collect(toList());
  }

  private Collection<String> combineSetsOfIds(List<String> firstSetOfIds,
    List<String> secondSetOfIds) {

    return Stream.concat(firstSetOfIds.stream(), secondSetOfIds.stream())
      .collect(toList());
  }
}
