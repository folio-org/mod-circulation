package org.folio.circulation.infrastructure.storage;

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CalendarRepositoryTest {

  private static final String EXPECTED_PATH =
    "%s/all-openings?startDate=%s&endDate=%s&includeClosed=false&limit=2147483647";

  @Test
  void testCreateCorrectRawQueryStringParameters() {
    Clients clients = mock(Clients.class);
    CollectionResourceClient collectionResourceClient = mock(
      CollectionResourceClient.class
    );
    when(clients.calendarStorageClient()).thenReturn(collectionResourceClient);
    when(collectionResourceClient.get(any(String.class)))
      .thenReturn(CompletableFuture.completedFuture(Result.succeeded(null)));

    String servicePointId = UUID.randomUUID().toString();
    ZonedDateTime startDate = ZonedDateTime.of(2020, 10, 12, 18, 0, 0, 0, UTC);
    ZonedDateTime endDate = ZonedDateTime.of(2020, 10, 22, 15, 30, 0, 0, UTC);

    CalendarRepository calendarRepository = new CalendarRepository(clients);
    calendarRepository.fetchOpeningDaysBetweenDates(
      servicePointId,
      startDate,
      endDate
    );

    ArgumentCaptor<String> paramsArgumentCaptor = ArgumentCaptor.forClass(
      String.class
    );
    verify(collectionResourceClient).get(paramsArgumentCaptor.capture());

    String actualPath = paramsArgumentCaptor.getValue();
    String expectedPath = String.format(
      EXPECTED_PATH,
      servicePointId,
      startDate.toLocalDate(),
      endDate.toLocalDate()
    );
    assertThat(actualPath, is(expectedPath));
  }
}
