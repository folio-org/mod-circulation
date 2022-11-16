package org.folio.circulation.support;

import static org.folio.circulation.support.AsyncCoordinationUtil.mapSequentially;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;

import lombok.SneakyThrows;

class AsyncCoordinationUtilTest {

  @Test
  @SneakyThrows
  void mapSequentiallyRunsFuturesInOrder() {
    List<Integer> numbers = IntStream.range(0, 1000)
      .boxed()
      .collect(Collectors.toList());

    List<Integer> mappingResults = new ArrayList<>();

    Function<Integer, CompletableFuture<Result<Integer>>> mapper = number ->
      CompletableFuture.supplyAsync(() -> mappingResults.add(number))
        .thenApply(r -> succeeded(number));

    List<Integer> invocationResults = mapSequentially(numbers, mapper)
      .get(5, TimeUnit.SECONDS)
      .value();

    assertEquals(numbers, mappingResults);
    assertEquals(numbers, invocationResults);
  }

}