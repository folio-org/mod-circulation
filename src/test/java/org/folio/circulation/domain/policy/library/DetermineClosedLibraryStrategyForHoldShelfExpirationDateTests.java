package org.folio.circulation.domain.policy.library;

import api.support.builders.ServicePointBuilder;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.TimePeriod;
import org.folio.circulation.domain.policy.ExpirationDateManagement;
import org.junit.Assert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Boolean.TRUE;
import static java.time.ZoneOffset.UTC;

class DetermineClosedLibraryStrategyForHoldShelfExpirationDateTests {
  private ClosedLibraryStrategy closedLibraryStrategy;

  @ParameterizedTest
  @MethodSource("testDetermineClosedLibraryStrategyHoldShelfParameters")
  void testDetermineClosedLibraryStrategyForHoldShelf(ExpirationDateManagement expirationDateManagement, Class<?> expectedClass) {
    JsonObject representation = new ServicePointBuilder("Circ Desk 2", "cd2",
      "Circulation Desk -- Back Entrance").withPickupLocation(TRUE)
      .withHoldShelfExpriyPeriod(6, "Days")
      .withholdShelfClosedLibraryDateManagement(expirationDateManagement.name())
      .create();

    ZonedDateTime startDate = ZonedDateTime.of(2019, 1, 1, 0, 0, 0, 0, UTC);
    TimePeriod period = new TimePeriod(6, "Days");
    closedLibraryStrategy = ClosedLibraryStrategyUtils
      .determineClosedLibraryStrategyForHoldShelfExpirationDate(expirationDateManagement, startDate, UTC, period);

    Assert.assertEquals(expectedClass, closedLibraryStrategy.getClass());
  }

  private static List<Object[]> testDetermineClosedLibraryStrategyHoldShelfParameters() {
    List<Object[]> data = new ArrayList<>();
    data.add(new Object[]{ExpirationDateManagement.KEEP_THE_CURRENT_DUE_DATE, KeepCurrentDateStrategy.class});
    data.add(new Object[]{ExpirationDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY, EndOfPreviousDayStrategy.class});
    data.add(new Object[]{ExpirationDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY, EndOfNextOpenDayStrategy.class});
    data.add(new Object[]{ExpirationDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS, EndOfCurrentHoursStrategy.class});
    data.add(new Object[]{ExpirationDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS, BeginningOfNextOpenHoursStrategy.class});
    return data;
  }
}
