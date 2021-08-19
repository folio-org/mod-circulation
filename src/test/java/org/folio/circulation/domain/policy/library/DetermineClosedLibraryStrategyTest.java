package org.folio.circulation.domain.policy.library;

import java.util.ArrayList;
import java.util.List;

import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;

public class DetermineClosedLibraryStrategyTest {

  private ClosedLibraryStrategy closedLibraryStrategy;

  @ParameterizedTest
  @MethodSource("testDetermineClosedLibraryStrategyParameters")
  public void testDetermineClosedLibraryStrategy(DueDateManagement dueDateManagement, Class<?> expectedClass) {
    JsonObject representation = new LoanPolicyBuilder()
      .withName("Loan policy")
      .withClosedLibraryDueDateManagement(dueDateManagement.getValue())
      .rolling(Period.days(5)).create();

    LoanPolicy loanPolicy = LoanPolicy.from(representation);
    DateTime startDate = new DateTime(2019, DateTimeConstants.JANUARY, 1, 0, 0);
    closedLibraryStrategy = ClosedLibraryStrategyUtils
      .determineClosedLibraryStrategy(loanPolicy, startDate, DateTimeZone.UTC);

    Assert.assertEquals(expectedClass, closedLibraryStrategy.getClass());
  }

  private static List<Object[]> testDetermineClosedLibraryStrategyParameters() {
    List<Object[]> data = new ArrayList<>();
    data.add(new Object[]{DueDateManagement.KEEP_THE_CURRENT_DUE_DATE, KeepCurrentDateStrategy.class});
    data.add(new Object[]{DueDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY, EndOfPreviousDayStrategy.class});
    data.add(new Object[]{DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY, EndOfNextOpenDayStrategy.class});
    data.add(new Object[]{DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS, EndOfCurrentHoursStrategy.class});
    data.add(new Object[]{DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS, BeginningOfNextOpenHoursStrategy.class});
    return data;
  }
}
