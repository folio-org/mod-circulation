package api.support.builders;

import org.folio.circulation.domain.OpeningDayPeriod;

import java.util.Arrays;
import java.util.List;

public class OpeningDayPeriodBuilder {
  private String serviceId;
  private List<OpeningDayPeriod> openingDays;

  public OpeningDayPeriodBuilder(String serviceId, OpeningDayPeriod... openingDays) {
    this.serviceId = serviceId;
    this.openingDays = Arrays.asList(openingDays);
  }

  String getServiceId() {
    return serviceId;
  }

  List<OpeningDayPeriod> getOpeningDays() {
    return openingDays;
  }

  public OpeningDayPeriod getLastPeriod() {
    return openingDays.get(openingDays.size() - 1);
  }

  public OpeningDayPeriod getCurrentPeriod() {
    return openingDays.get(openingDays.size() / 2);
  }

  public OpeningDayPeriod getFirstPeriod() {
    return openingDays.get(0);
  }
}
