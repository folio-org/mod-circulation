package api.support.fixtures.policies;

import api.support.http.IndividualResource;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public final class PoliciesToActivate {
  private final IndividualResource loanPolicy;
  private final IndividualResource requestPolicy;
  private final IndividualResource noticePolicy;
  private final IndividualResource overduePolicy;
  private final IndividualResource lostItemPolicy;
}
