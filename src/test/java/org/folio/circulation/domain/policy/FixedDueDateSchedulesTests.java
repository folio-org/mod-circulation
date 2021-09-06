package org.folio.circulation.domain.policy;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class FixedDueDateSchedulesTests {
  @Test
  void shouldHaveNoSchedulesWhenPropertyMissingInJSON() {
    final FixedDueDateSchedules schedules = FixedDueDateSchedules.from(new JsonObject());

    assertThat(schedules.isEmpty(), is(true));
  }
}
