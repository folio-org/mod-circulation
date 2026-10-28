package org.folio.circulation.domain.anonymization.service;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.anonymization.UserLoanAnonymizationPreference;
import org.folio.circulation.infrastructure.storage.loans.UserLoanAnonymizationPreferenceRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

/**
 * Nightly resync of the users' loan anonymization preferences from mod-users into
 * mod-circulation-storage. Repairs what the users topic events missed: lost events, users deleted
 * by CQL query, an empty table after a reinstall.
 *
 * <p>All pages are fetched before anything is written, so a failed fetch never removes a row.
 * A preference changed by an event while the resync runs can be overwritten with the state
 * fetched just before; the window is the time between fetching that user's page and the write.
 */
public class UserLoanAnonymizationPreferenceSyncService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final List<String> PREFERENCES_DEVIATING_FROM_LIBRARY_POLICY =
    List.of("asEarlyAsPossible", "never");

  private final UserRepository userRepository;
  private final UserLoanAnonymizationPreferenceRepository preferenceRepository;
  private final PageLimit pageLimit;

  public UserLoanAnonymizationPreferenceSyncService(UserRepository userRepository,
    UserLoanAnonymizationPreferenceRepository preferenceRepository, PageLimit pageLimit) {

    this.userRepository = userRepository;
    this.preferenceRepository = preferenceRepository;
    this.pageLimit = pageLimit;
  }

  public CompletableFuture<Result<Void>> sync() {
    long start = System.currentTimeMillis();

    return fetchAllPages(null, new ArrayList<>())
      .thenCompose(r -> r.after(preferences -> {
        log.info("sync:: fetched {} users with a loan anonymization preference in {} ms",
          preferences.size(), System.currentTimeMillis() - start);
        return preferenceRepository.replaceAll(preferences);
      }))
      .thenApply(r -> r.map(ignored -> {
        log.info("sync:: done in {} ms", System.currentTimeMillis() - start);
        return null;
      }));
  }

  private CompletableFuture<Result<List<UserLoanAnonymizationPreference>>> fetchAllPages(
    String afterId, List<UserLoanAnonymizationPreference> fetched) {

    return userRepository.findUsersWithLoanAnonymizationPreference(
        PREFERENCES_DEVIATING_FROM_LIBRARY_POLICY, afterId, pageLimit)
      .thenCompose(r -> r.after(users -> {
        users.forEach(user -> fetched.add(new UserLoanAnonymizationPreference(
          user.getId(), user.getLoanAnonymizationPreference())));

        if (users.size() < pageLimit.getLimit()) {
          return completedFuture(succeeded(fetched));
        }
        User lastUser = users.get(users.size() - 1);
        return fetchAllPages(lastUser.getId(), fetched);
      }));
  }
}
