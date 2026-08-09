package api;

import static api.support.APITestContext.TENANT_ID;
import static api.support.fakes.FakePubSub.clearRegistrationData;
import static api.support.fakes.FakePubSub.getCreatedEventTypes;
import static api.support.fakes.FakePubSub.getDeletedEventTypes;
import static api.support.fakes.FakePubSub.getRegisteredPublishers;
import static api.support.fakes.FakePubSub.getRegisteredSubscribers;
import static api.support.fakes.FakePubSub.setFailPubSubRegistration;
import static api.support.fakes.FakePubSub.setFailPubSubUnregistering;
import static api.support.matchers.EventTypeMatchers.isItemAgedToLostEventType;
import static api.support.matchers.EventTypeMatchers.isItemCheckedInEventType;
import static api.support.matchers.EventTypeMatchers.isItemCheckedOutEventType;
import static api.support.matchers.EventTypeMatchers.isItemClaimedReturnedEventType;
import static api.support.matchers.EventTypeMatchers.isItemDeclaredLostEventType;
import static api.support.matchers.EventTypeMatchers.isLoanClosedEventType;
import static api.support.matchers.EventTypeMatchers.isLoanDueDateChangedEventType;
import static api.support.matchers.EventTypeMatchers.isLogRecordEventType;
import static api.support.matchers.PubSubRegistrationMatchers.isValidPublishersRegistration;
import static api.support.matchers.PubSubRegistrationMatchers.isValidSubscribersRegistration;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.Arrays;
import java.util.List;

import org.folio.circulation.domain.events.CirculationKafkaTopic;
import org.folio.circulation.resources.TenantActivationResource;
import org.folio.circulation.rules.cache.CirculationRulesCache;
import org.folio.circulation.rules.cache.Rules;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;

class TenantActivationResourceTests extends APITests {

  @BeforeEach
  public void init() {
    clearRegistrationData();
    setFailPubSubRegistration(false);
    setFailPubSubUnregistering(false);
  }

  @Test
  void tenantActivationFailsWhenCannotRegisterWithPubSub() {
    setFailPubSubRegistration(true);

    Response response = tenantActivationFixture.postTenant();
    assertThat(response.getStatusCode(), is(HTTP_INTERNAL_SERVER_ERROR.toInt()));
  }

  @Test
  void tenantActivationSucceedsWhenCanRegisterInPubSub() {
    Response response = tenantActivationFixture.postTenant();

    assertThat(response.getStatusCode(), is(HTTP_CREATED.toInt()));

    assertThat(getCreatedEventTypes().size(), is(8));
    assertThat(getRegisteredPublishers().size(), is(1));

    assertThat(getCreatedEventTypes(), hasItems(
      isItemCheckedOutEventType(),
      isItemCheckedInEventType(),
      isItemDeclaredLostEventType(),
      isItemAgedToLostEventType(),
      isLoanDueDateChangedEventType(),
      isItemClaimedReturnedEventType(),
      isLoanClosedEventType(),
      isLogRecordEventType()
    ));

    assertThat(getRegisteredPublishers(), hasItem(isValidPublishersRegistration()));
    assertThat(getRegisteredSubscribers(), hasItem(isValidSubscribersRegistration()));
  }

  @Test
  void tenantDeactivationSucceedsWhenCannotUnregisterInPubSub() {
    setFailPubSubUnregistering(true);

    Response response = tenantActivationFixture.deleteTenant();

    assertThat(response.getStatusCode(), is(HTTP_NO_CONTENT.toInt()));
    assertThat(getDeletedEventTypes().size(), is(0));
  }

  @Test
  void tenantActivationWarmsUpCirculationRulesCache() {
    CirculationRulesCache.getInstance().dropCache();
    assertThat(CirculationRulesCache.getInstance().getRules(TENANT_ID), nullValue());
    Response response = tenantActivationFixture.postTenant();
    assertThat(response, hasStatus(HTTP_CREATED));
    Rules cachedRules = CirculationRulesCache.getInstance().getRules(TENANT_ID);
    assertThat(cachedRules, not(nullValue()));
    assertThat(cachedRules.getRulesAsText(), equalTo(circulationRulesFixture.getCirculationRules()));
  }

  @Test
  void kafkaTopicsAreCreatedAndDeleted() {
    TenantActivationResource.enableNativeKafkaIntegration();

    List<String> circulationTopics = Arrays.stream(CirculationKafkaTopic.values())
      .map(topic -> topic.fullTopicName(TENANT_ID))
      .toList();

    kafkaHelper.deleteTopics(circulationTopics);

    Response postResponse1 = tenantActivationFixture.postTenant();
    assertThat(postResponse1, hasStatus(HTTP_CREATED));
    kafkaHelper.verifyTopicsExist(circulationTopics);

    Response deleteResponse = tenantActivationFixture.deleteTenant(true);
    assertThat(deleteResponse, hasStatus(HTTP_NO_CONTENT));
    kafkaHelper.verifyTopicsDoNotExist(circulationTopics);

    // recreate tenant to avoid breaking other tests
    Response postResponse2 = tenantActivationFixture.postTenant();
    assertThat(postResponse2, hasStatus(HTTP_CREATED));
    kafkaHelper.verifyTopicsExist(circulationTopics);
  }
}
