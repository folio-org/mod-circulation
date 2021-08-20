package api.support.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
class User {
  private String id;
  private String barcode;
}
