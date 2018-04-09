package org.folio.circulation;

import static org.junit.Assert.assertTrue;

import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Test some random uuids against the uuid regexp used in ramls/circulation.raml.
 * <p>
 * <a href="https://en.wikipedia.org/wiki/Universally_unique_identifier">https://en.wikipedia.org/wiki/Universally_unique_identifier<a>
 * <p>
 * <a href="https://stackoverflow.com/questions/7905929/how-to-test-valid-uuid-guid">https://stackoverflow.com/questions/7905929/how-to-test-valid-uuid-guid</a>
 */
public class UUIDRegExpTest {
  private String regexp = "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[1-5][a-fA-F0-9]{3}-[89abAB][a-fA-F0-9]{3}-[a-fA-F0-9]{12}$";
  private Pattern pattern = Pattern.compile(regexp);

  @Test
  public void randomUUID() {
    for (int i=0; i<500; i++) {
      String uuid = UUID.randomUUID().toString();
      assertTrue(uuid, pattern.matcher(uuid).matches());
    }
  }
}
