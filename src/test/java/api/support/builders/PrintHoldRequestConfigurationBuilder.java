package api.support.builders;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.With;

@With
@AllArgsConstructor
@NoArgsConstructor(force = true)
public class PrintHoldRequestConfigurationBuilder extends JsonBuilder implements Builder {

    private final boolean printHoldRequestsEnabled;

    @Override
    public JsonObject create() {
        JsonObject request = new JsonObject();
        request.put("printHoldRequestsEnabled", printHoldRequestsEnabled);
        return request;
    }
}
