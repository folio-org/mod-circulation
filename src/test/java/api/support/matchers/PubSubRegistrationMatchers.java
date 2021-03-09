package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.folio.util.pubsub.PubSubClientUtils.constructModuleName;
import static org.hamcrest.core.Is.is;

import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public class PubSubRegistrationMatchers {
  public static Matcher<JsonObject> isValidPublishersRegistration() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("moduleId", is(constructModuleName())),
      hasJsonPath("eventDescriptors[0].eventType", is("ITEM_CHECKED_OUT")),
      hasJsonPath("eventDescriptors[1].eventType", is("ITEM_CHECKED_IN")),
      hasJsonPath("eventDescriptors[2].eventType", is("ITEM_DECLARED_LOST")),
      hasJsonPath("eventDescriptors[3].eventType", is("ITEM_AGED_TO_LOST")),
      hasJsonPath("eventDescriptors[4].eventType", is("ITEM_CLAIMED_RETURNED")),
      hasJsonPath("eventDescriptors[5].eventType", is("LOAN_DUE_DATE_CHANGED")),
      hasJsonPath("eventDescriptors[6].eventType", is("LOG_RECORD"))
    );
  }

  public static Matcher<JsonObject> isValidSubscribersRegistration() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("moduleId", is(constructModuleName())),
      hasJsonPath("subscriptionDefinitions[0].eventType",
        is("LOAN_RELATED_FEE_FINE_CLOSED"))
    );
  }
}
