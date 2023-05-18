package api.requests.scenarios;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.ItemBuilder.PAGED;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.singletonList;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.TlrFeatureStatus;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class PageRequestWorkflowTests extends APITests {
  @Test
  void canBeFulfilledWithoutPriorCheckIn() {
    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();

    IndividualResource requestByJessica = requestsFixture.place(new RequestBuilder()
      .page()
      .fulfillToHoldShelf()
      .withItemId(smallAngryPlanet.getId())
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .withRequestDate(ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC))
      .withRequesterId(jessica.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response getByIdResponse = requestsClient.getById(requestByJessica.getId());
    assertThat(getByIdResponse, hasStatus(HTTP_OK));
    assertThat(getByIdResponse.getJson().getString("status"), is(CLOSED_FILLED));

    IndividualResource checkedOutSmallAngryPlanet = itemsClient.get(smallAngryPlanet);
    assertThat(checkedOutSmallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  void itemCannotBeCheckedOutToOtherPatronWhenItemIsPagedAndNotYetBeingFulfilled() {

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource rebecca = usersFixture.rebecca();

    IndividualResource requestByJessica = requestsFixture.place(new RequestBuilder()
      .page()
      .fulfillToHoldShelf()
      .withItemId(smallAngryPlanet.getId())
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .withRequestDate(ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC))
      .withRequesterId(jessica.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    Response response = checkOutFixture.attemptCheckOutByBarcode(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Stuart, Rebecca " +
        "because it has been requested by another patron"),
      hasParameter("userBarcode", rebecca.getJson().getString("barcode")))));

    Response getByIdResponse = requestsClient.getById(requestByJessica.getId());

    assertThat(getByIdResponse, hasStatus(HTTP_OK));

    assertThat(getByIdResponse.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    IndividualResource pagedSmallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(pagedSmallAngryPlanet, hasItemStatus(PAGED));
  }

  @Test
  void titleLevelRequestConfirmationNoticeWithValidLocationShouldBeSentWithEnabledTlr() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED, null, null, null);

    UUID templateId = UUID.randomUUID();
    templateFixture.createDummyNoticeTemplate(templateId);
    use(new NoticePolicyBuilder()
      .withName("Test patron notice policy")
      .withLoanNotices(singletonList(
        new NoticeConfigurationBuilder()
          .withTemplateId(templateId)
          .withEventType(NoticeEventType.PAGING_REQUEST.getRepresentation())
          .create())));

    ItemResource itemResource = itemsFixture.basedUponSmallAngryPlanet();
    RequestBuilder requestBuilder = new RequestBuilder()
      .page()
      .titleRequestLevel()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withInstanceId(itemResource.getInstanceId())
      .withRequesterId(usersFixture.charlotte().getId())
      .withRequestDate(getZonedDateTime())
      .withStatus(OPEN_NOT_YET_FILLED)
      .withPickupServicePoint(servicePointsFixture.cd1());

    verifyNumberOfSentNotices(0);
    requestsFixture.place(requestBuilder);
    List<JsonObject> notices = verifyNumberOfSentNotices(1);
    JsonObject item = notices.get(0).getJsonObject("context").getJsonObject("item");

    assertThat(item.getString("effectiveLocationCampus"), notNullValue());
    assertThat(item.getString("effectiveLocationInstitution"), notNullValue());
  }
}
