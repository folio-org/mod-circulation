package org.folio.circulation.api.support.matchers;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.JsonArrayHelper;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.List;

public class JsonObjectMatchers {
  public static TypeSafeMatcher<JsonObject> hasSoleErrorMessageContaining(String message) {
    return new TypeSafeMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a sole validation error message containing: %s", message));
      }

      @Override
      protected boolean matchesSafely(JsonObject body) {
        System.out.println(String.format("Inspecting for validation errors: %s",
          body.encodePrettily()));

        if(!body.containsKey("errors")) {
          return false;
        }

        final List<JsonObject> errors = JsonArrayHelper.toList(body.getJsonArray("errors"));

        if(errors.size() == 1) {
          return errors.get(0).getString("message").contains(message);
        }
        else
          return false;
      }
    };
  }

}
