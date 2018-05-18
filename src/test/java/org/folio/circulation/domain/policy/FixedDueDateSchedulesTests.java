package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class FixedDueDateSchedulesTests {
  @Test
  public void shouldHaveNoSchedulesWhenPropertyMissingInJSON() {
    final FixedDueDateSchedules schedules = new FixedDueDateSchedules(new JsonObject());

    assertThat(schedules.isEmpty(), is(true));
  }
}
