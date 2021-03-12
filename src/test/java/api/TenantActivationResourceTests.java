package api;

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
import static api.support.matchers.EventTypeMatchers.isLoanDueDateChangedEventType;
import static api.support.matchers.EventTypeMatchers.isLogRecordEventType;
import static api.support.matchers.PubSubRegistrationMatchers.isValidPublishersRegistration;
import static api.support.matchers.PubSubRegistrationMatchers.isValidSubscribersRegistration;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_INTERNAL_SERVER_ERROR;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.folio.circulation.support.http.client.Response;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;

public class TenantActivationResourceTests extends APITests {

  @Before
  public void init() {
    setFailPubSubRegistration(false);
    setFailPubSubUnregistering(false);
  }

  @Test
  public void tenantActivationFailsWhenCannotRegisterWithPubSub() {
    setFailPubSubRegistration(true);

    Response response = tenantActivationFixture.postTenant();
    assertThat(response.getStatusCode(), is(HTTP_INTERNAL_SERVER_ERROR.toInt()));
  }

  @Test
  public void tenantActivationSucceedsWhenCanRegisterInPubSub() {
    Response response = tenantActivationFixture.postTenant();

    assertThat(response.getStatusCode(), is(HTTP_CREATED.toInt()));

    assertThat(getCreatedEventTypes().size(), is(7));
    assertThat(getRegisteredPublishers().size(), is(1));

    assertThat(getCreatedEventTypes(), hasItems(
      isItemCheckedOutEventType(),
      isItemCheckedInEventType(),
      isItemDeclaredLostEventType(),
      isItemAgedToLostEventType(),
      isLoanDueDateChangedEventType(),
      isItemClaimedReturnedEventType(),
      isLogRecordEventType()
    ));

    assertThat(getRegisteredPublishers(), hasItem(isValidPublishersRegistration()));
    assertThat(getRegisteredSubscribers(), hasItem(isValidSubscribersRegistration()));
  }

  @Test
  public void tenantDeactivationSucceedsWhenCannotUnregisterInPubSub() {
    setFailPubSubUnregistering(true);

    Response response = tenantActivationFixture.deleteTenant();

    assertThat(response.getStatusCode(), is(HTTP_NO_CONTENT.toInt()));
    assertThat(getDeletedEventTypes().size(), is(0));
  }
}
