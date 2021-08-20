package api.support.data.events.log;

import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class ChangedRequest {
  public final String id;
  public final String requestType;
  public final String oldRequestStatus;
  public final String newRequestStatus;
}
