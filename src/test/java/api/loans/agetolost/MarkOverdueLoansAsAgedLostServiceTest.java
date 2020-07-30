package api.loans.agetolost;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static org.folio.circulation.support.Clients.create;
import static org.folio.circulation.support.http.OkapiHeader.OKAPI_URL;
import static org.folio.circulation.support.http.OkapiHeader.REQUEST_ID;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;
import static org.folio.circulation.support.http.OkapiHeader.TOKEN;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.services.agedtolost.MarkOverdueLoansAsAgedLostService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.spring.SpringApiTest;
import api.support.spring.clients.ScheduledJobClient;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import lombok.val;

public class MarkOverdueLoansAsAgedLostServiceTest extends SpringApiTest {
  @Autowired
  private ScheduledJobClient scheduledAgeToLostClient;
  private Clients clients;

  @Before
  public void createClients() {
    val httpClient = Vertx.vertx().createHttpClient();
    val routingContext = mock(RoutingContext.class);
    val serverRequest = mock(HttpServerRequest.class);
    val headers = mock(MultiMap.class);
    val okapiHeaders = getOkapiHeadersFromContext();

    when(routingContext.request()).thenReturn(serverRequest);

    when(serverRequest.getHeader(OKAPI_URL)).thenReturn(okapiHeaders.getUrl().toString());
    when(serverRequest.getHeader(TENANT)).thenReturn(okapiHeaders.getTenantId());
    when(serverRequest.getHeader(TOKEN)).thenReturn(okapiHeaders.getToken());
    when(serverRequest.getHeader(REQUEST_ID)).thenReturn("Age to lost process");
    when(serverRequest.headers()).thenReturn(headers);

    when(headers.contains(anyString())).thenReturn(true);

    clients = create(new WebContext(routingContext), httpClient);
  }

  @Test
  public void shouldPickFirstThousandLoansAndRestProcessOnNextExecution() throws Exception {
    useLostItemPolicy(lostItemFeePoliciesFixture.ageToLostAfterOneMinute().getId());

    val maximumLoans = 5;
    val allItems = checkOutItems(maximumLoans + maximumLoans / 2);

    val service = new MarkOverdueLoansAsAgedLostService(clients, maximumLoans);

    // 1st run of the process
    service.processAgeToLost().get(5, TimeUnit.SECONDS);

    // Expect the very 5 loans to be aged to lost
    allItems.stream()
      .limit(maximumLoans)
      .forEach(item -> assertThat(itemsClient.get(item).getJson(), isAgedToLost()));

    // remaining should be checked out
    allItems.stream()
      .skip(maximumLoans)
      .forEach(item -> assertThat(itemsClient.get(item).getJson(), isCheckedOut()));

    // 2nd run of the process
    service.processAgeToLost().get(5, TimeUnit.SECONDS);

    // Expect the remaining 2 loans to be aged to lost
    allItems.stream()
      .skip(maximumLoans)
      .forEach(item -> assertThat(itemsClient.get(item).getJson(), isAgedToLost()));
  }

  private DateTime getLoanOverdueDate() {
    return now(UTC).minusWeeks(3);
  }

  private List<IndividualResource> checkOutItems(int numberOfItems) {
    val items = new ArrayList<IndividualResource>();

    for (int offsetMinutes = numberOfItems; offsetMinutes > 0; offsetMinutes--) {
      val overdueItem = itemsFixture.basedUponNod(
        itemBuilder -> itemBuilder.withBarcode(generateString()));

      checkOutFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder()
          .forItem(overdueItem)
          .at(servicePointsFixture.cd1())
          .to(usersFixture.charlotte())
          // the first will have the biggest overdue
          .on(getLoanOverdueDate().minusMinutes(2).minusMinutes(offsetMinutes)));

      items.add(overdueItem);
    }

    return items;
  }
}
