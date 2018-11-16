package api.support.builders;

import java.util.UUID;

public class Address {
  private UUID type;

  Address(UUID type) {
    this.type = type;
  }

  UUID getType() {
    return type;
  }
}
