package api.support.builders;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.OpeningDay;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OpeningDayPeriodBuilder {

  private String serviceId;
  private AdjacentOpeningDays openingDays;

  public OpeningDay getLastPeriod() {
    return openingDays.getNextDay();
  }

  public OpeningDay getCurrentPeriod() {
    return openingDays.getRequestedDay();
  }

  public OpeningDay getFirstPeriod() {
    return openingDays.getPreviousDay();
  }
}
