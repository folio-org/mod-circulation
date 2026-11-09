package api.loans.anonymization;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.fakes.FakeUserLoanAnonymizationPreferencesResource.storePreference;
import static api.support.fakes.FakeUserLoanAnonymizationPreferencesResource.storedPreferences;
import static api.support.http.InterfaceUrls.scheduledLoanAnonymizationPreferenceSyncUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

import api.support.APITests;
import api.support.builders.UserBuilder;
import api.support.http.TimedTaskClient;

class ScheduledUserLoanAnonymizationPreferenceSyncTests extends APITests {
  private final TimedTaskClient timedTaskClient =
    new TimedTaskClient(getOkapiHeadersFromContext());

  @Test
  void storesExactlyTheUsersWhoDeviateFromTheLibraryPolicy() {
    String neverUser = createUser("never");
    String earlyUser = createUser("asEarlyAsPossible");
    String libraryPolicyUser = createUser("libraryPolicy");
    createUserWithoutPreference();
    String missedUpdate = createUser("never");
    storePreference(missedUpdate, "asEarlyAsPossible");
    storePreference(libraryPolicyUser, "never");
    storePreference(UUID.randomUUID().toString(), "never");

    timedTaskClient.start(scheduledLoanAnonymizationPreferenceSyncUrl(), 204,
      "scheduled-loan-anonymization-preference-sync");

    assertThat(storedPreferences(), is(Map.of(neverUser, "never",
      earlyUser, "asEarlyAsPossible", missedUpdate, "never")));
  }

  private String createUser(String preference) {
    var user = newUser().put("loanAnonymizationPreference", preference);
    return usersClient.create(user).getId().toString();
  }

  private void createUserWithoutPreference() {
    usersClient.create(newUser());
  }

  private static JsonObject newUser() {
    return new UserBuilder()
      .withUsername("user-" + UUID.randomUUID())
      .create();
  }
}
