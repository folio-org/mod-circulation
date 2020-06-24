package api.support.dto;

import java.util.Date;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OverrideRenewal {
  private final String itemBarcode;
  private final String userBarcode;
  private final String comment;
  private Date dueDate;
  private String servicePointId;
}
