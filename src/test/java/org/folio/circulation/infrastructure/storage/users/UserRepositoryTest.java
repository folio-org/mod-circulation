package org.folio.circulation.infrastructure.storage.users;

import static org.folio.circulation.support.http.client.PageLimit.oneThousand;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRepositoryTest {
  @Mock
  private Clients clients;
  @Mock
  private CollectionResourceClient usersStorageClient;

  @Test
  void usersWithLoanAnonymizationPreferenceArePagedById() {
    when(clients.usersStorage()).thenReturn(usersStorageClient);
    when(usersStorageClient.getMany(any(CqlQuery.class), any(PageLimit.class)))
      .thenReturn(ofAsync(new Response(200, "{\"users\": []}", "application/json")));

    new UserRepository(clients).findUsersWithLoanAnonymizationPreference(
      List.of("asEarlyAsPossible", "never"), "5a7b2c55-0000-4000-8000-000000000000",
      oneThousand());

    ArgumentCaptor<CqlQuery> query = ArgumentCaptor.forClass(CqlQuery.class);
    verify(usersStorageClient).getMany(query.capture(), any(PageLimit.class));
    assertThat(URLDecoder.decode(query.getValue().encode().value(), StandardCharsets.UTF_8),
      is("loanAnonymizationPreference==(\"asEarlyAsPossible\" or \"never\")"
        + " and id>\"5a7b2c55-0000-4000-8000-000000000000\" sortBy id/sort.ascending"));
  }
}
