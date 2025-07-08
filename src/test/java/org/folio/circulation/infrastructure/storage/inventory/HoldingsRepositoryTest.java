package org.folio.circulation.infrastructure.storage.inventory;

import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldingsRepositoryTest {

  @Mock
  private CollectionResourceClient holdingsClient;

  @InjectMocks
  private HoldingsRepository holdingsRepository;

  @Test
  void holdingsAreFetchedByInstanceIdsInBatches() {
    Mockito.when(holdingsClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(ofAsync(new Response(200, "{}", "application/json")));

    int idsPerBatch = 50;
    List<String> instanceIds = IntStream.range(0, idsPerBatch + 1)
      .boxed()
      .map(i -> UUID.randomUUID().toString())
      .toList();

    holdingsRepository.fetchByInstances(instanceIds);

    ArgumentCaptor<CqlQuery> queryCaptor = ArgumentCaptor.forClass(CqlQuery.class);
    verify(holdingsClient, times(2)).getMany(queryCaptor.capture(), any(PageLimit.class));
    List<CqlQuery> actualQueries = queryCaptor.getAllValues();
    assertEquals(2, actualQueries.size());

    CqlQuery expectedQuery1 = exactMatchAny("instanceId", instanceIds.subList(0, idsPerBatch)).value();
    CqlQuery expectedQuery2 = exactMatchAny("instanceId", instanceIds.subList(idsPerBatch, instanceIds.size())).value();

    assertEquals(expectedQuery1, actualQueries.get(0));
    assertEquals(expectedQuery2, actualQueries.get(1));
  }

}