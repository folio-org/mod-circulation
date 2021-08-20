package api.support.data.events.log;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
class CheckInLogEvent {
  public final String loanId;
  public final List<ChangedRequest> changedRequests;

  public ChangedRequest firstChangedRequest() {
    return changedRequests.get(0);
  }
}
