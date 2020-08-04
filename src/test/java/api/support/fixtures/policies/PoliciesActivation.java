package api.support.fixtures.policies;

import org.folio.circulation.support.http.client.IndividualResource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public final class PoliciesActivation {
  private final IndividualResource loanPolicy;
  private final IndividualResource requestPolicy;
  private final IndividualResource noticePolicy;
  private final IndividualResource overduePolicy;
  private final IndividualResource lostItemPolicy;
}
