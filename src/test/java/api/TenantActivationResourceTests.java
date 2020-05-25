package api;

import static api.support.matchers.EventTypeMatchers.isItemCheckedInEventType;
import static api.support.matchers.EventTypeMatchers.isItemCheckedOutEventType;
import static api.support.matchers.EventTypeMatchers.isItemDeclaredLostEventType;
import static api.support.matchers.EventTypeMatchers.isLoanDueDateChangedEventType;
import static api.support.matchers.PubSubRegistrationMatchers.isValidPublishersRegistration;
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
import api.support.fakes.FakePubSub;
import api.support.matchers.EventTypeMatchers;

public class TenantActivationResourceTests extends APITests {

  @Before
  public void init() {
    FakePubSub.setFailPubSubRegistration(false);
    FakePubSub.setFailPubSubUnregistering(false);
  }

  @Test
  public void tenantActivationFailsWhenCannotRegisterWithPubSub() {
    FakePubSub.setFailPubSubRegistration(true);

    Response response = tenantActivationFixture.postTenant();
    assertThat(response.getStatusCode(), is(HTTP_INTERNAL_SERVER_ERROR.toInt()));
  }

  @Test
  public void tenantActivationSucceedsWhenCanRegisterInPubSub() {
    Response response = tenantActivationFixture.postTenant();
    assertThat(response.getStatusCode(), is(HTTP_CREATED.toInt()));
    assertThat(FakePubSub.getCreatedEventTypes().size(), is(4));
    assertThat(FakePubSub.getRegisteredPublishers().size(), is(1));
    assertThat(FakePubSub.getCreatedEventTypes(), hasItems(
      isItemCheckedOutEventType(),
      isItemCheckedInEventType(),
      isItemDeclaredLostEventType(),
      isLoanDueDateChangedEventType()
    ));
    assertThat(FakePubSub.getRegisteredPublishers(), hasItem(isValidPublishersRegistration()));
  }

  @Test
  public void tenantDeactivationFailsWhenCannotUnregisterWithPubSub() {
    FakePubSub.setFailPubSubUnregistering(true);

    Response response = tenantActivationFixture.deleteTenant();
    assertThat(response.getStatusCode(), is(HTTP_INTERNAL_SERVER_ERROR.toInt()));
  }

  @Test
  public void tenantDeactivationSucceedsWhenCanUnregisterInPubSub() {
    Response response = tenantActivationFixture.deleteTenant();
    assertThat(response.getStatusCode(), is(HTTP_NO_CONTENT.toInt()));
    assertThat(FakePubSub.getDeletedEventTypes().size(), is(4));
    assertThat(FakePubSub.getDeletedEventTypes(), hasItems(
      EventTypeMatchers.ITEM_CHECKED_OUT,
      EventTypeMatchers.ITEM_CHECKED_IN,
      EventTypeMatchers.ITEM_DECLARED_LOST,
      EventTypeMatchers.LOAN_DUE_DATE_CHANGED
    ));
  }

}
