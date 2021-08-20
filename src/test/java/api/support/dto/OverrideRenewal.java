package api.support.dto;

import api.support.builders.RenewBlockOverrides;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
class OverrideRenewal {
  private final String itemBarcode;
  private final String userBarcode;
  private String servicePointId;
  private RenewBlockOverrides overrideBlocks;
}
