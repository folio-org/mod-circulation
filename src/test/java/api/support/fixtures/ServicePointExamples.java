package api.support.fixtures;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import api.support.builders.ServicePointBuilder;
import org.folio.circulation.domain.policy.ExpirationDateManagement;

import java.util.UUID;

public class ServicePointExamples {
  static ServicePointBuilder basedUponCircDesk1() {
    return new ServicePointBuilder("Circ Desk 1", "cd1",
        "Circulation Desk -- Hallway").withPickupLocation(TRUE)
        .withHoldShelfExpriyPeriod(30, "Days");
  }

  static ServicePointBuilder basedUponCircDesk2() {
    return new ServicePointBuilder("Circ Desk 2", "cd2",
        "Circulation Desk -- Back Entrance").withPickupLocation(TRUE)
        .withHoldShelfExpriyPeriod(6, "Months");
  }

  static ServicePointBuilder basedUponCircDesk3() {
    return new ServicePointBuilder("Circ Desk 3", "cd3",
        "Circulation Desk -- Dumpster").withPickupLocation(FALSE);
  }

  static ServicePointBuilder basedUponCircDesk4() {
    return new ServicePointBuilder("Circ Desk 4", "cd4",
        "Circulation Desk -- Basement").withPickupLocation(TRUE)
        .withHoldShelfExpriyPeriod(2, "Weeks");
  }

  static ServicePointBuilder basedUponCircDesk5() {
    return new ServicePointBuilder("Circ Desk 5", "cd5",
        "Circulation Desk -- Rooftop").withPickupLocation(TRUE)
        .withHoldShelfExpriyPeriod(42, "Minutes");
  }

  static ServicePointBuilder basedUponCircDesk6() {
    return new ServicePointBuilder("Circ Desk 6", "cd6",
        "Circulation Desk -- Igloo").withPickupLocation(TRUE)
        .withId(UUID.fromString(CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID))
        .withholdShelfClosedLibraryDateManagement(ExpirationDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS.getValue())
        .withHoldShelfExpriyPeriod(6, "Hours");
  }
}
