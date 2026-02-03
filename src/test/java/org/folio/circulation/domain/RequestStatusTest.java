package org.folio.circulation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class RequestStatusTest {

  @Test
  void closedStatesReturnsAllClosedStatuses() {
    List<String> closedStates = RequestStatus.closedStates();

    assertEquals(4, closedStates.size());
    assertTrue(closedStates.contains("Closed - Filled"));
    assertTrue(closedStates.contains("Closed - Cancelled"));
    assertTrue(closedStates.contains("Closed - Unfilled"));
    assertTrue(closedStates.contains("Closed - Pickup expired"));
  }

  @Test
  void closedStatesDoesNotContainOpenStatuses() {
    List<String> closedStates = RequestStatus.closedStates();

    assertTrue(!closedStates.contains("Open - Not yet filled"));
    assertTrue(!closedStates.contains("Open - Awaiting pickup"));
    assertTrue(!closedStates.contains("Open - In transit"));
    assertTrue(!closedStates.contains("Open - Awaiting delivery"));
  }
}
