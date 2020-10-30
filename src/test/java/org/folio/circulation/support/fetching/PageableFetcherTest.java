package org.folio.circulation.support.fetching;

import static java.lang.Math.min;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.folio.circulation.support.http.client.CqlQuery.noQuery;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.noInteractions;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Offset;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.junit.Test;

public class PageableFetcherTest {
  @Test
  public void shouldProcessPages() {
    final var pageSize = limit(10);
    final var repository = spy(repository(100));
    final var pageProcessor = spy(dummyProcessor());

    final var voidResult = processPages(repository, pageSize, pageProcessor);

    assertThat(voidResult.succeeded(), is(true));
    // 10 calls to fetch all records + last to make sure everything is fetched
    verify(pageProcessor, times(11)).processPage(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldAbortFetchingWhenPageProcessorRaisedError() {
    final var failureMessage = "Fetch failure";
    final var pageSize = limit(10);
    final var repository = repository(50);
    final PageProcessor<Integer> pageProcessor = mock(PageProcessor.class);

    when(pageProcessor.processPage(any()))
      .thenReturn(completedFuture(failed(new ServerErrorFailure(failureMessage))));

    final var voidResult = processPages(repository, pageSize, pageProcessor);

    assertThat(voidResult.failed(), is(true));
    assertThat(voidResult.cause().toString(), containsString(failureMessage));
    verify(pageProcessor, times(1)).processPage(any());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldAbortFetchingWhenFailedToFetch() {
    final var failureMessage = "Fetch failure";
    final var pageSize = limit(10);
    final GetManyRecordsRepository<Integer> repository = mock(GetManyRecordsRepository.class);
    final PageProcessor<Integer> pageProcessor = mock(PageProcessor.class);

    when(repository.getMany(any(), any(), any()))
      .thenReturn(completedFuture(failed(new ServerErrorFailure(failureMessage))));

    final var voidResult = processPages(repository, pageSize, pageProcessor);

    assertThat(voidResult.failed(), is(true));
    assertThat(voidResult.cause().toString(), containsString(failureMessage));
    verify(pageProcessor, noInteractions()).processPage(any());
  }

  @Test
  public void shouldAbortFetchingWhenRecordCountLimitIsReached() {
    final var pageSize = limit(10);
    final var recordLimit = 90;
    final GetManyRecordsRepository<Integer> repository = repository(recordLimit + 10);
    final PageProcessor<Integer> pageProcessor = spy(dummyProcessor());

    final var voidResult = new PageableFetcher<>(repository, pageSize, recordLimit)
      .processPages(noQuery().value(), pageProcessor)
      .getNow(Result.failed(new ServerErrorFailure("Time out")));

    assertThat(voidResult.failed(), is(true));
    verify(pageProcessor, times(9)).processPage(any());
    assertThat(voidResult.cause().toString(), containsString(
      "Maximum allowed item count is set to 90 and it has been reached"));
  }

  @Test
  public void shouldNotAbortFetchingWhenRecordCountLimitIsReachedButAllPagesFetched() {
    final var pageSize = limit(10);
    final var recordLimit = 91;
    final GetManyRecordsRepository<Integer> repository = repository(recordLimit);
    final PageProcessor<Integer> pageProcessor = spy(dummyProcessor());

    final var voidResult = new PageableFetcher<>(repository, pageSize, recordLimit)
      .processPages(noQuery().value(), pageProcessor)
      .getNow(Result.failed(new ServerErrorFailure("Time out")));

    assertThat(voidResult.succeeded(), is(true));
    verify(pageProcessor, times(10)).processPage(any());
  }

  private <T> Result<Void> processPages(GetManyRecordsRepository<T> repository,
    PageLimit pageLimit, PageProcessor<T> processor) {

    return new PageableFetcher<>(repository, pageLimit, 1000)
      .processPages(noQuery().value(), processor)
      .getNow(Result.failed(new ServerErrorFailure("Time out")));
  }

  // Mockito can not spy a lambda
  @SuppressWarnings("all")
  private GetManyRecordsRepository<Integer> repository(int totalRecords) {
    return new GetManyRecordsRepository<Integer>() {
      @Override
      public CompletableFuture<Result<MultipleRecords<Integer>>> getMany(
        CqlQuery cqlQuery, PageLimit pageLimit, Offset offset) {

        final var start = offset.getOffset();
        final var end = min(start + pageLimit.getLimit(), totalRecords);
        final var response = range(start, end).boxed().collect(toList());

        return ofAsync(() -> new MultipleRecords<>(response, totalRecords));
      }
    };
  }

  // Mockito can not spy a lambda
  @SuppressWarnings("all")
  private PageProcessor<Integer> dummyProcessor() {
    return new PageProcessor<Integer>() {
      @Override
      public CompletableFuture<Result<Void>> processPage(MultipleRecords<Integer> records) {
        return ofAsync(() -> null);
      }
    };
  }
}
