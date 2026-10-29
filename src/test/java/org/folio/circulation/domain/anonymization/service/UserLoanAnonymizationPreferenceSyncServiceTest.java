package org.folio.circulation.domain.anonymization.service;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.http.client.PageLimit.limit;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.User;
import org.folio.circulation.domain.anonymization.UserLoanAnonymizationPreference;
import org.folio.circulation.infrastructure.storage.loans.UserLoanAnonymizationPreferenceRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.OngoingStubbing;

import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class UserLoanAnonymizationPreferenceSyncServiceTest {
  private static final PageLimit PAGE_LIMIT = limit(2);

  @Mock
  private UserRepository userRepository;
  @Mock
  private UserLoanAnonymizationPreferenceRepository preferenceRepository;

  @Test
  void fetchesAllPagesByIdThenReplacesOnce() {
    User first = user("never");
    User second = user("asEarlyAsPossible");
    User third = user("never");
    whenPageAfter(null).thenReturn(completedFuture(succeeded(List.of(first, second))));
    whenPageAfter(second.getId()).thenReturn(completedFuture(succeeded(List.of(third))));
    when(preferenceRepository.replaceAll(any()))
      .thenReturn(completedFuture(succeeded(null)));

    assertThat(service().sync().join().succeeded(), is(true));

    assertThat(replacedPreferences(), contains(preference(first), preference(second),
      preference(third)));
  }

  @Test
  void fullLastPageIsFollowedByAnEmptyPage() {
    User first = user("never");
    User second = user("never");
    whenPageAfter(null).thenReturn(completedFuture(succeeded(List.of(first, second))));
    whenPageAfter(second.getId()).thenReturn(completedFuture(succeeded(List.of())));
    when(preferenceRepository.replaceAll(any()))
      .thenReturn(completedFuture(succeeded(null)));

    assertThat(service().sync().join().succeeded(), is(true));

    assertThat(replacedPreferences(), contains(preference(first), preference(second)));
  }

  @Test
  void failedPageReplacesNothing() {
    User second = user("never");
    whenPageAfter(null).thenReturn(completedFuture(succeeded(List.of(user("never"), second))));
    whenPageAfter(second.getId())
      .thenReturn(completedFuture(failed(new ServerErrorFailure("mod-users not available"))));

    assertThat(service().sync().join().failed(), is(true));

    verify(preferenceRepository, never()).replaceAll(any());
  }

  @Test
  void noDeviatingUserEmptiesTheTable() {
    whenPageAfter(null).thenReturn(completedFuture(succeeded(List.of())));
    when(preferenceRepository.replaceAll(any()))
      .thenReturn(completedFuture(succeeded(null)));

    assertThat(service().sync().join().succeeded(), is(true));

    assertThat(replacedPreferences(), is(empty()));
  }

  private UserLoanAnonymizationPreferenceSyncService service() {
    return new UserLoanAnonymizationPreferenceSyncService(userRepository, preferenceRepository,
      PAGE_LIMIT);
  }

  private OngoingStubbing<CompletableFuture<Result<List<User>>>> whenPageAfter(String afterId) {

    return when(userRepository.findUsersWithLoanAnonymizationPreference(anyCollection(),
      afterId == null ? isNull() : eq(afterId), eq(PAGE_LIMIT)));
  }

  @SuppressWarnings("unchecked")
  private List<UserLoanAnonymizationPreference> replacedPreferences() {
    ArgumentCaptor<List<UserLoanAnonymizationPreference>> captor =
      ArgumentCaptor.forClass(List.class);
    verify(preferenceRepository).replaceAll(captor.capture());
    return captor.getValue();
  }

  private static User user(String preference) {
    return new User(new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("loanAnonymizationPreference", preference));
  }

  private static UserLoanAnonymizationPreference preference(User user) {
    return new UserLoanAnonymizationPreference(user.getId(),
      user.getLoanAnonymizationPreference());
  }
}
