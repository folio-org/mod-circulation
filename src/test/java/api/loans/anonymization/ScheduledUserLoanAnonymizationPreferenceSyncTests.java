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

import api.support.APITests;
import api.support.builders.UserBuilder;
import api.support.http.TimedTaskClient;

class ScheduledUserLoanAnonymizationPreferenceSyncTests extends APITests {
  private final TimedTaskClient timedTaskClient =
    new TimedTaskClient(getOkapiHeadersFromContext());

  @Test
  void storesExactlyTheUsersWhoDeviateFromTheLibraryPolicy() {
    String neverUser = user("never");
    String earlyUser = user("asEarlyAsPossible");
    String libraryPolicyUser = user("libraryPolicy");
    user(null);
    String missedUpdate = user("never");
    storePreference(missedUpdate, "asEarlyAsPossible");
    storePreference(libraryPolicyUser, "never");
    storePreference(UUID.randomUUID().toString(), "never");

    timedTaskClient.start(scheduledLoanAnonymizationPreferenceSyncUrl(), 204,
      "scheduled-loan-anonymization-preference-sync");

    assertThat(storedPreferences(), is(Map.of(neverUser, "never",
      earlyUser, "asEarlyAsPossible", missedUpdate, "never")));
  }

  private String user(String preference) {
    var user = new UserBuilder()
      .withUsername("user-" + UUID.randomUUID())
      .create();
    if (preference != null) {
      user.put("loanAnonymizationPreference", preference);
    }
    return usersClient.create(user).getId().toString();
  }
}
