package org.folio.circulation.domain.policy;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class FixedDueDateSchedulesTests {
  @Test
  void shouldHaveNoSchedulesWhenPropertyMissingInJSON() {
    final FixedDueDateSchedules schedules = FixedDueDateSchedules.from(new JsonObject());

    assertThat(schedules.isEmpty(), is(true));
  }
}
