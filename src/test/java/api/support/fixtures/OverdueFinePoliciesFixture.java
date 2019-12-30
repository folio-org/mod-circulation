package api.support.fixtures;

import api.support.builders.NoticePolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class OverdueFinePoliciesFixture {
  private final RecordCreator overdueFinePolicyRecordCreator;

  public OverdueFinePoliciesFixture(ResourceClient overdueFinePoliciesClient) {
    overdueFinePolicyRecordCreator = new RecordCreator(overdueFinePoliciesClient,
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource facultyStandard() {

    JsonObject overdueFine = new JsonObject();
    overdueFine.put("quantity", 5.0);
    overdueFine.put("intervalId", "day");

    JsonObject overdueRecallFine = new JsonObject();
    overdueRecallFine.put("quantity", 1.0);
    overdueRecallFine.put("intervalId", "hour");


    final OverdueFinePolicyBuilder facultyStandard = new OverdueFinePolicyBuilder()
      .withName("Faculty standard")
      .withDescription("This is description for Faculty standard")
      .withOverdueFine(overdueFine)
      .withCountClosed(true)
      .withMaxOverdueFine(50.00)
      .withForgiveOverdueFine(true)
      .withOverdueRecallFine(overdueRecallFine)
      .withGracePeriodRecall(false)
      .withMaxOverdueRecallFine(50.00);

    return overdueFinePolicyRecordCreator.createIfAbsent(facultyStandard);
  }

  public IndividualResource create(NoticePolicyBuilder noticePolicy) {
    return overdueFinePolicyRecordCreator.createIfAbsent(noticePolicy);
  }
}
