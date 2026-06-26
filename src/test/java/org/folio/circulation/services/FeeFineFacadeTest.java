package org.folio.circulation.services;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.AccountCancelReason.CANCELLED_ITEM_RETURNED;
import static org.folio.circulation.domain.AccountRefundReason.LOST_ITEM_FOUND;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Campus;
import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Institution;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Library;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;
import org.folio.circulation.services.feefine.AccountActionResponse;
import org.folio.circulation.services.support.CreateAccountCommand;
import org.folio.circulation.services.support.RefundAndCancelAccountCommand;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class FeeFineFacadeTest {
  @Mock
  private Clients clients;
  @Mock
  private CollectionResourceClient accountClient;
  @Mock
  private CollectionResourceClient accountActionsClient;
  @Mock
  private CollectionResourceClient userClient;
  @Mock
  private CollectionResourceClient servicePointClient;
  @Mock
  private CollectionResourceClient accountRefundClient;
  private FeeFineFacade feeFineFacade;

  @BeforeEach
  public void setUp() {
    when(clients.accountsStorageClient()).thenReturn(accountClient);
    when(clients.feeFineActionsStorageClient()).thenReturn(accountActionsClient);
    when(clients.usersStorage()).thenReturn(userClient);
    when(clients.servicePointsStorage()).thenReturn(servicePointClient);
    when(clients.accountsRefundClient()).thenReturn(accountRefundClient);

    feeFineFacade = new FeeFineFacade(clients);
  }

  @Test
  void shouldForwardFailureIfAnAccountIsNotCreated() {
    final String expectedError = "Fee fine account failed to be created";

    when(accountClient.post(any()))
      .thenAnswer(postRespondWithRequestAndFail(expectedError));

    final Result<List<FeeFineAction>> result = feeFineFacade.createAccounts(Arrays.asList(
      createCommandBuilder().build(),
      createCommandBuilder().build()))
      .getNow(null);

    assertThat(result, notNullValue());

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
    assertThat(((ServerErrorFailure) result.cause()).getReason(), is(expectedError));
  }

  @Test
  void shouldForwardFailureIfAnAccountIsNotRefunded() throws Exception {
    final String expectedError = "Fee fine account failed to be refunded";

    when(accountRefundClient.post(any(JsonObject.class), anyString()))
      .thenAnswer(postRespondWithRequestAndFail(expectedError));

    User user = User.from(new JsonObject()
      .put("personal", new JsonObject()
        .put("firstName", "Folio")
        .put("lastName", "Tester")));

    final Result<AccountActionResponse> result = feeFineFacade
      .refundAccountIfNeeded(refundCommand(), user)
      .get(5, TimeUnit.SECONDS);

    assertThat(result, notNullValue());

    assertThat(result.failed(), is(true));
    assertThat(result.cause(), instanceOf(ServerErrorFailure.class));
    assertThat(((ServerErrorFailure) result.cause()).getReason(), is(expectedError));
  }

  private CreateAccountCommand.CreateAccountCommandBuilder createCommandBuilder() {
    final Item item = Item.from(new JsonObject())
      .withLocation(new Location(null, "Main library", null, null, emptyList(),
        null, false, Institution.unknown(), Campus.unknown(),
        Library.unknown(), ServicePoint.unknown()));

    return CreateAccountCommand.builder()
      .withAmount(new FeeAmount(10.0))
      .withCurrentServicePointId("cd1-id")
      .withFeeFine(new FeeFine("fee-id", "owner-id", "Lost item fee"))
      .withFeeFineOwner(new FeeFineOwner("owner-id", "Cd1 owner", singletonList("sp-id")))
      .withStaffUserId("user-id")
      .withItem(item)
      .withLoan(Loan.from(new JsonObject().put("id", UUID.randomUUID().toString())));
  }

  private RefundAndCancelAccountCommand refundCommand() {
    final JsonObject account = new JsonObject()
      .put("feeFineType", "Lost item fee")
      .put("amount", 50.0)
      .put("remaining", 0.0)
      .put("id", UUID.randomUUID().toString());

    final FeeFineAction paidAction = FeeFineAction.from(new JsonObject()
      .put("typeAction", "Paid fully")
      .put("amountAction", 50.0));

    return new RefundAndCancelAccountCommand(Account.from(account)
      .withFeeFineActions(singletonList(paidAction)), "user-id", "sp-id",
      LOST_ITEM_FOUND, CANCELLED_ITEM_RETURNED);
  }

  private Response jsonResponse(int status, JsonObject json) {
    return new Response(status, json.toString(), "application/json");
  }

  private Response emptyJsonResponse(int status) {
    return jsonResponse(status, new JsonObject());
  }

  private Answer<CompletableFuture<Result<Response>>> postRespondWithRequestAndFail(String reason) {
    return new Answer<>() {
      private int requestNumber = 0;

      @Override
      public CompletableFuture<Result<Response>> answer(InvocationOnMock mock) {
        requestNumber++;

        return requestNumber > 1
          ? ofAsync(() -> jsonResponse(201, mock.getArgument(0)))
          : completedFuture(failed(new ServerErrorFailure(reason)));
      }
    };
  }
}
