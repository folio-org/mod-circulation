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

    return fetchAllUserSpecificPreferences(null, new ArrayList<>())
      .thenApply(r -> r.peek(preferences -> logFetched(preferences, start)))
      .thenCompose(r -> r.after(preferenceRepository::replaceAll))
      .thenApply(r -> r.peek(ignored -> logSyncDone(start)));
  }

  private void logFetched(List<UserLoanAnonymizationPreference> preferences, long start) {
    log.info("sync:: fetched {} users with a loan anonymization preference in {} ms",
      preferences.size(), System.currentTimeMillis() - start);
  }

  private void logSyncDone(long start) {
    log.info("sync:: done in {} ms", System.currentTimeMillis() - start);
  }

  private CompletableFuture<Result<List<UserLoanAnonymizationPreference>>>
    fetchAllUserSpecificPreferences(String afterId, List<UserLoanAnonymizationPreference> fetched) {

    return userRepository.findUsersWithLoanAnonymizationPreference(
        PREFERENCES_DEVIATING_FROM_LIBRARY_POLICY, afterId, pageLimit)
      .thenCompose(r -> r.after(page -> addPageAndFetchRest(page, fetched)));
  }

  private CompletableFuture<Result<List<UserLoanAnonymizationPreference>>> addPageAndFetchRest(
    List<User> page, List<UserLoanAnonymizationPreference> fetched) {

    page.forEach(user -> fetched.add(UserLoanAnonymizationPreference.from(user)));

    boolean isLastPage = page.size() < pageLimit.getLimit();
    if (isLastPage) {
      return completedFuture(succeeded(fetched));
    }
    return fetchAllUserSpecificPreferences(page.getLast().getId(), fetched);
  }
}
