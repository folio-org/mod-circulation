package org.folio.circulation.domain.representations;

import static org.folio.circulation.domain.notice.TemplateContextUtil.createCheckInContext;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.http.server.JsonHttpResponse.ok;

import org.folio.circulation.domain.CheckInContext;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.support.http.server.HttpResponse;

import io.vertx.core.json.JsonObject;

import java.util.concurrent.ExecutionException;

public class CheckInByBarcodeResponse {
  private final CheckInContext context;

  private final PatronGroupRepository patronGroupRepository;

  public static CheckInByBarcodeResponse fromRecords(CheckInContext records, PatronGroupRepository patronGroupRepository) {
    return new CheckInByBarcodeResponse(records, patronGroupRepository);
  }

  private CheckInByBarcodeResponse(CheckInContext context, PatronGroupRepository patronGroupRepository) {
    this.context = context;
    this.patronGroupRepository = patronGroupRepository;
  }

  public HttpResponse toHttpResponse() {
    return ok(this.toJson());
  }

  private  final String REQUESTER = "requester";

  private JsonObject toJson() {
    final LoanRepresentation loanRepresentation = new LoanRepresentation();
    final ItemSummaryRepresentation itemRepresentation = new ItemSummaryRepresentation();

    final JsonObject json = new JsonObject();
    String patronGroup = null;

    if(context.getHighestPriorityFulfillableRequest() != null) {
     patronGroup = determinePatronGroup(context.getHighestPriorityFulfillableRequest());
    }

    System.out.println("Patron Group " + patronGroup);
    write(json, "loan", loanRepresentation.extendedLoan(context.getLoan()));
    write(json, "item", itemRepresentation.createItemSummary(context.getItem()));
    final JsonObject staffSlipJson = createCheckInContext(context);

    if(patronGroup != null) {
      write(json, "staffSlipContext",
        staffSlipJson.put(REQUESTER, staffSlipJson.getJsonObject(REQUESTER).put("patronGroup", patronGroup)));
    }else{
      write(json, "staffSlipContext", staffSlipJson);
    }

    write(json, "inHouseUse", context.isInHouseUse());

//    System.out.println("Before change" + createCheckInContext(context));
//    JsonObject requesterJson = createCheckInContext(context).getJsonObject(REQUESTER);
//    JsonObject reqJsonWithPG = requesterJson.put("patronGroup", patronGroup);
//    //JsonObject finalJson = createCheckInContext(context).put(REQUESTER,reqJsonWithPG);
//    JsonObject finalJson = createCheckInContext(context).put(REQUESTER,createCheckInContext(context).getJsonObject(REQUESTER).put("patronGroup", patronGroup));
//    System.out.println("Final Json" + finalJson);

    return json;
  }

  private String determinePatronGroup(Request request) {
    try {
      if(request.getRequester() != null) {
        return patronGroupRepository.findGroupForUser(context.getHighestPriorityFulfillableRequest().getRequester())
          .thenApply(r -> r.map(User::getPatronGroup)).get().value().getGroup();
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    return null;
  }
}
