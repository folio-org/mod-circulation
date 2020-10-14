package api.requests;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedNoticeLogRecordEventsCountIsEqualTo;
import static api.support.matchers.EventMatchers.isValidLoanDueDateChangedEvent;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.EventType.LOAN_DUE_DATE_CHANGED;
import static org.folio.circulation.domain.EventType.LOG_RECORD;
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_REASON_NAME;
import static org.folio.circulation.domain.representations.RequestProperties.CANCELLATION_REASON_PUBLIC_DESCRIPTION;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_CREATED;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_UPDATED;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.Request;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.fakes.FakePubSub;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class RequestsAPIUpdatingTests extends APITests {

  private static final String REQUEST_CANCELLATION = "Request cancellation";

  @Test
  public void canReplaceAnExistingRequest() {

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(steve)
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    final IndividualResource charlotte = usersFixture.charlotte();

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .by(charlotte)
        .withTags(new RequestBuilder.Tags(Arrays.asList("new", "important")))
    );

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(createdRequest.getId()));
    assertThat(representation.getString("requestType"), is("Hold"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(temeraire.getId()));
    assertThat(representation.getString("requesterId"), is(charlotte.getId()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("Temeraire"));

    assertThat("barcode is taken from item",
      representation.getJsonObject("item").getString("barcode"),
      is("232142443432"));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Broadwell"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Charlotte"));

    assertThat("middle name is not taken from requesting user",
      representation.getJsonObject("requester").containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("6430705932"));

    assertThat(representation.containsKey("tags"), is(true));
    final JsonObject tagsRepresentation = representation.getJsonObject("tags");

    assertThat(tagsRepresentation.containsKey("tagList"), is(true));
    assertThat(tagsRepresentation.getJsonArray("tagList"), contains("new", "important"));
  }

  //TODO: Check does not have pickup service point any more
  @Test
  public void canReplaceAnExistingRequestWithDeliveryAddress() {

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource workAddressType = addressTypesFixture.work();

    final IndividualResource charlotte = usersFixture.charlotte(userBuilder -> userBuilder
      .withAddress(
        new Address(workAddressType.getId(),
          "Fake first address line",
          "Fake second address line",
          "Fake city",
          "Fake region",
          "Fake postal code",
          "Fake country code")));

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(temeraire)
      .by(charlotte)
      .withRequestDate(requestDate)
      .fulfilToHoldShelf(exampleServicePoint.getId()));

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .deliverToAddress(workAddressType.getId()));

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(createdRequest.getId()));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("Request should have a delivery address",
      representation.containsKey("deliveryAddress"), is(true));

    final JsonObject deliveryAddress = representation.getJsonObject("deliveryAddress");

    assertThat(deliveryAddress.getString("addressTypeId"), is(workAddressType.getId()));
    assertThat(deliveryAddress.getString("addressLine1"), is("Fake first address line"));
    assertThat(deliveryAddress.getString("addressLine2"), is("Fake second address line"));
    assertThat(deliveryAddress.getString("city"), is("Fake city"));
    assertThat(deliveryAddress.getString("region"), is("Fake region"));
    assertThat(deliveryAddress.getString("postalCode"), is("Fake postal code"));
    assertThat(deliveryAddress.getString("countryId"), is("Fake country code"));
  }

  @Test
  public void replacingAnExistingRequestRemovesItemInformationWhenItemDoesNotExist() {

    final ItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(usersFixture.steve()));

    itemsClient.delete(nod.getId());

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold());

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("itemId"), is(nod.getId()));

    assertThat("has no item information when item no longer exists",
      representation.containsKey("item"), is(false));
  }

  @Test
  public void replacingAnExistingRequestRemovesRequesterInformationWhenUserDoesNotExist() {

    final ItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod);

    val requester = usersFixture.steve();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(requester));

    usersFixture.remove(requester);

    requestsClient.replace(createdRequest.getId(), RequestBuilder.from(createdRequest));

    final IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("requesterId"), is(requester.getId()));

    assertThat("has no requesting user information taken when user no longer exists",
      representation.containsKey("requester"), is(false));
  }

  @Test
  public void replacingAnExistingRequestRemovesRequesterBarcodeWhenNonePresent() {

    final ItemResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod);

    final IndividualResource steve = usersFixture.steve();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(steve));

    final JsonObject userToReplace = steve.copyJson();

    userToReplace.remove("barcode");

    usersClient.replace(steve.getId(), userToReplace);

    requestsClient.replace(createdRequest.getId(), RequestBuilder.from(createdRequest));

    final IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("requesterId"), is(steve.getId()));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Jones"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Steven"));

    assertThat("barcode is not present when requesting user does not have one",
      representation.getJsonObject("requester").containsKey("barcode"),
      is(false));
  }

  @Test
  public void replacingAnExistingRequestIncludesRequesterMiddleNameWhenPresent() {

    final ItemResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod);

    final IndividualResource steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(steve));

    final JsonObject userToReplace = steve.copyJson();

    final JsonObject personalDetails = userToReplace.getJsonObject("personal");

    write(personalDetails, "middleName", "Carter");

    usersClient.replace(steve.getId(), userToReplace);

    requestsClient.replace(createdRequest.getId(), RequestBuilder.from(createdRequest));

    final IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("requesterId"), is(steve.getId()));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Jones"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Steven"));

    assertThat("middle name is taken from requesting user",
      representation.getJsonObject("requester").getString("middleName"),
      is("Carter"));
  }

  @Test
  public void replacingAnExistingRequestRemovesItemBarcodeWhenNonePresent() {

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire);

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve()));

    final JsonObject itemToReplace = temeraire.copyJson();

    itemToReplace.remove("barcode");

    itemsClient.replace(temeraire.getId(), itemToReplace);

    requestsClient.replace(createdRequest.getId(), createdRequest.copyJson());

    IndividualResource fetchedRequest = requestsClient.get(createdRequest.getId());

    JsonObject representation = fetchedRequest.getJson();

    assertThat(representation.getString("itemId"), is(temeraire.getId()));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("Temeraire"));

    assertThat("barcode is not taken from item",
      representation.getJsonObject("item").containsKey("barcode"),
      is(false));
  }

  @Test
  public void cannotReplaceAnExistingRequestWithServicePointThatIsNotForPickup() {

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(temeraire)
      .by(usersFixture.steve())
      .fulfilToHoldShelf(exampleServicePoint.getId()));

    UUID badServicePointId = servicePointsFixture.cd3().getId();

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .withPickupServicePointId(badServicePointId));

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Service point is not a pickup location"),
      hasUUIDParameter("pickupServicePointId", badServicePointId))));
  }

  @Test
  public void cannotReplaceAnExistingRequestWithUnknownPickupLocation() {

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .forItem(temeraire)
        .by(usersFixture.james())
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId()));

    UUID badServicePointId = UUID.randomUUID();

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .withPickupServicePointId(badServicePointId));

    assertThat(putResponse, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Pickup service point does not exist"),
      hasUUIDParameter("pickupServicePointId", badServicePointId))));
  }

  @Test
  public void cancellationReasonPublicDescriptionIsUsedAsReasonForCancellationToken() {

    UUID requestCancellationTemplateId = UUID.randomUUID();
    JsonObject requestCancellationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(requestCancellationTemplateId)
      .withEventType(REQUEST_CANCELLATION)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with request cancellation notice")
      .withLoanNotices(Collections.singletonList(requestCancellationConfiguration));
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource requester = usersFixture.steve();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();
    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .page()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(requester)
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    final IndividualResource itemNotAvailable = cancellationReasonsFixture.itemNotAvailable();
    JsonObject updatedRequest = RequestBuilder.from(createdRequest)
      .cancelled()
      .withCancellationReasonId(itemNotAvailable.getId())
      .withCancellationAdditionalInformation("Cancellation info")
      .create();
    requestsClient.replace(createdRequest.getId(), updatedRequest);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(temeraire, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(updatedRequest));
    noticeContextMatchers.putAll(TemplateContextMatchers.getCancelledRequestContextMatchers(updatedRequest));
    noticeContextMatchers.put("request.reasonForCancellation",
      is(itemNotAvailable.getJson().getString(CANCELLATION_REASON_PUBLIC_DESCRIPTION)));
    assertThat(sentNotices,
      hasItems(
        hasEmailNoticeProperties(requester.getId(), requestCancellationTemplateId, noticeContextMatchers)));
    assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(patronNoticesClient.getAll().size());
  }

  @Test
  public void cancellationReasonNameIsUsedAsReasonForCancellationTokenWhenPublicDescriptionIsNotPresent() {

    UUID requestCancellationTemplateId = UUID.randomUUID();
    JsonObject requestCancellationConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(requestCancellationTemplateId)
      .withEventType(REQUEST_CANCELLATION)
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with request cancellation notice")
      .withLoanNotices(Collections.singletonList(requestCancellationConfiguration));
    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource requester = usersFixture.steve();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();
    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .page()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(requester)
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    final IndividualResource courseReserves = cancellationReasonsFixture.courseReserves();
    JsonObject updatedRequest = RequestBuilder.from(createdRequest)
      .cancelled()
      .withCancellationReasonId(courseReserves.getId())
      .withCancellationAdditionalInformation("Cancellation info")
      .create();
    requestsClient.replace(createdRequest.getId(), updatedRequest);

    Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(patronNoticesClient::getAll, Matchers.hasSize(1));
    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(TemplateContextMatchers.getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(temeraire, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(updatedRequest));
    noticeContextMatchers.putAll(TemplateContextMatchers.getCancelledRequestContextMatchers(updatedRequest));
    noticeContextMatchers.put("request.reasonForCancellation",
      is(courseReserves.getJson().getString(CANCELLATION_REASON_NAME)));
    assertThat(sentNotices,
      hasItems(
        hasEmailNoticeProperties(requester.getId(), requestCancellationTemplateId, noticeContextMatchers)));
    assertThatPublishedNoticeLogRecordEventsCountIsEqualTo(patronNoticesClient.getAll().size());
  }

  @Test
  public void replacedRequestShouldOnlyIncludeStoredPropertiesInStorage() {

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();

    proxyRelationshipsFixture.currentProxyFor(steve, charlotte);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(steve)
        .proxiedBy(charlotte)
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    requestsClient.replace(createdRequest.getId(), createdRequest.copyJson());

    Response getStorageRequestResponse = requestsStorageClient.getById(createdRequest.getId());

    assertThat(String.format("Failed to get request: %s", getStorageRequestResponse.getBody()),
      getStorageRequestResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject storageRepresentation = getStorageRequestResponse.getJson();

    assertThat("has information taken from requesting user",
      storageRepresentation.containsKey("requester"), is(true));

    final JsonObject requesterSummary = storageRepresentation.getJsonObject("requester");

    assertThat("last name is taken from requesting user",
      requesterSummary.getString("lastName"), is("Jones"));

    assertThat("first name is taken from requesting user",
      requesterSummary.getString("firstName"), is("Steven"));

    assertThat("patron group information should not be stored for requesting user",
      requesterSummary.containsKey("patronGroup"), is(false));

    assertThat("has information taken from proxying user",
      storageRepresentation.containsKey("proxy"), is(true));

    final JsonObject proxySummary = storageRepresentation.getJsonObject("proxy");

    assertThat("last name is taken from proxying user",
      proxySummary.getString("lastName"), is("Broadwell"));

    assertThat("first name is taken from proxying user",
      proxySummary.getString("firstName"), is("Charlotte"));

    assertThat("patron group information should not be stored for proxying user",
      proxySummary.containsKey("patronGroup"), is(false));
  }

  @Test
  public void canReplaceRequestWithAnInactiveUser() {

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(steve)
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    final IndividualResource inactiveCharlotte
      = usersFixture.charlotte(UserBuilder::inactive);

    final Response putResponse = requestsClient.attemptReplace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .by(inactiveCharlotte)
        .withTags(new RequestBuilder.Tags(Arrays.asList("new", "important"))));

    assertThat(putResponse, hasStatus(HTTP_NO_CONTENT));
  }

  @Test
  public void instanceIdentifiersAreUpdatedWhenRequestIsUpdated() {
    final UUID instanceId = UUID.randomUUID();
    final UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();
    final String isbnValue = "9780866989732";

    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet(
      identity(),
      instanceBuilder -> instanceBuilder.withId(instanceId),
      identity());

    IndividualResource request = requestsClient.create(
      new RequestBuilder()
        .page()
        .forItem(item)
        .by(usersFixture.steve())
        .fulfilToHoldShelf()
        .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    JsonObject updatedInstance = instancesClient.getById(instanceId).getJson().copy()
      .put("identifiers", new JsonArray().add(new JsonObject()
        .put("identifierTypeId", isbnIdentifierId.toString())
        .put("value", isbnValue)));

    instancesClient.replace(instanceId, updatedInstance);

    requestsClient.replace(request.getId(), request.copyJson());

    JsonArray identifiers = requestsClient.getById(request.getId())
      .getJson().getJsonObject("item").getJsonArray("identifiers");

    assertThat(identifiers, CoreMatchers.notNullValue());
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers.getJsonObject(0).getString("identifierTypeId"),
      is(isbnIdentifierId.toString()));
    assertThat(identifiers.getJsonObject(0).getString("value"),
      is(isbnValue));
  }

  @Test
  public void dueDateChangedEventIsPublished() {

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    IndividualResource loan = checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource steve = usersFixture.steve();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    final IndividualResource exampleServicePoint = servicePointsFixture.cd1();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .forItem(temeraire)
        .by(steve)
        .fulfilToHoldShelf()
        .withPickupServicePointId(exampleServicePoint.getId())
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    final IndividualResource charlotte = usersFixture.charlotte();

    requestsClient.replace(createdRequest.getId(),
      RequestBuilder.from(createdRequest)
        .hold()
        .by(charlotte)
        .withTags(new RequestBuilder.Tags(Arrays.asList("new", "important")))
    );

    Response response = loansClient.getById(loan.getId());
    JsonObject updatedLoan = response.getJson();

    // There should be ten events published - for "check out", for "log event: check out",
    // for "log event: request created", for "log event: request updated" for "recall" and for "replace"
    // and four log events for loans
    List<JsonObject> publishedEvents = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(10));

    Map<String, List<JsonObject>> events = publishedEvents.stream().collect(groupingBy(o -> o.getString("eventType")));

    Map<String, List<JsonObject>> logEvents = events.get(LOG_RECORD.name()).stream()
      .collect(groupingBy(e -> new JsonObject(e.getString("eventPayload")).getString("logEventType")));

    Request requestCreatedFromEventPayload = Request.from(new JsonObject(logEvents.get(REQUEST_CREATED.value()).get(0).getString("eventPayload")).getJsonObject("requests").getJsonObject("created"));
    assertThat(requestCreatedFromEventPayload, notNullValue());

    Request originalCreatedFromEventPayload = Request.from(new JsonObject(logEvents.get(REQUEST_UPDATED.value()).get(0).getString("eventPayload")).getJsonObject("requests").getJsonObject("original"));
    Request updatedCreatedFromEventPayload = Request.from(new JsonObject(logEvents.get(REQUEST_UPDATED.value()).get(0).getString("eventPayload")).getJsonObject("requests").getJsonObject("updated"));

    assertThat(requestCreatedFromEventPayload.getRequestType(), equalTo(originalCreatedFromEventPayload.getRequestType()));
    assertThat(originalCreatedFromEventPayload.getRequestType(), not(equalTo(updatedCreatedFromEventPayload.getRequestType())));

    assertThat(events.get(LOAN_DUE_DATE_CHANGED.name()).get(0), isValidLoanDueDateChangedEvent(updatedLoan));
  }
}
