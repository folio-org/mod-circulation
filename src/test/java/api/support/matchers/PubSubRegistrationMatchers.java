package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.core.Is.is;

import org.folio.util.pubsub.PubSubClientUtils;
import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public class PubSubRegistrationMatchers {
  public static Matcher<JsonObject> isValidPublishersRegistration() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("moduleId", is(PubSubClientUtils.constructModuleName())),
      hasJsonPath("eventDescriptors[0].eventType", is("ITEM_CHECKED_OUT")),
      hasJsonPath("eventDescriptors[1].eventType", is("ITEM_CHECKED_IN")),
      hasJsonPath("eventDescriptors[2].eventType", is("ITEM_DECLARED_LOST")),
      hasJsonPath("eventDescriptors[3].eventType", is("LOAN_DUE_DATE_CHANGED"))
    );
  }
}
