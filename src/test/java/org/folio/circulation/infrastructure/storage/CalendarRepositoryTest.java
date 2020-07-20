package org.folio.circulation.infrastructure.storage;

import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.CalendarRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class CalendarRepositoryTest {
  private static final String QUERY_PARAMETERS = "servicePointId=%s&startDate=%s&endDate=%s&includeClosedDays=false&limit=10000";

  @Test
  public void shouldCreateCorrectRawQueryStringParameters() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient collectionResourceClient = mock(CollectionResourceClient.class);
    when(clients.calendarStorageClient()).thenReturn(collectionResourceClient);
    when(collectionResourceClient.getManyWithRawQueryStringParameters(any(String.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

    String servicePointId = UUID.randomUUID().toString();
    DateTime startDate = new DateTime(2020, 10, 12, 18, 0, 0, UTC);
    DateTime endDate = new DateTime(2020, 10, 22, 15, 30, 0, UTC);

    CalendarRepository calendarRepository = new CalendarRepository(clients);
    calendarRepository.fetchOpeningDaysBetweenDates(servicePointId, startDate, endDate, false);

    ArgumentCaptor<String> paramsArgumentCaptor = ArgumentCaptor.forClass(String.class);
    verify(collectionResourceClient).getManyWithRawQueryStringParameters(paramsArgumentCaptor.capture());

    String actualParams = paramsArgumentCaptor.getValue();
    String expectedParams = String.format(QUERY_PARAMETERS, servicePointId,
      startDate.toLocalDate(), endDate.toLocalDate().plusDays(1));
    assertEquals(expectedParams, actualParams);
  }
}
