package org.folio.circulation.resources;

import org.folio.circulation.infrastructure.storage.ConfigurationRepository;
import org.folio.circulation.infrastructure.storage.SettingsRepository;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.loans.LostItemPolicyRepository;
import org.folio.circulation.infrastructure.storage.loans.OverdueFinePolicyRepository;
import org.folio.circulation.infrastructure.storage.notices.PatronNoticePolicyRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestQueueRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.infrastructure.storage.users.PatronGroupRepository;
import org.folio.circulation.infrastructure.storage.users.UserRepository;
import org.folio.circulation.support.Clients;

import lombok.Getter;

@Getter
class CheckOutRelatedRepositories {
  private final Clients clients;
  private final RequestRepository requestRepository;
  private final RequestQueueRepository requestQueueRepository;
  private final ItemRepository itemRepository;
  private final UserRepository userRepository;
  private final LoanRepository loanRepository;
  private final PatronNoticePolicyRepository patronNoticePolicyRepository;
  private final PatronGroupRepository patronGroupRepository;
  private final SettingsRepository settingsRepository;
  private final LoanPolicyRepository loanPolicyRepository;
  private final OverdueFinePolicyRepository overdueFinePolicyRepository;
  private final LostItemPolicyRepository lostItemPolicyRepository;
  private final ConfigurationRepository configurationRepository;

  public CheckOutRelatedRepositories(Clients clients) {
    this.clients = clients;
    this.requestRepository = new RequestRepository(clients);
    this.requestQueueRepository = new RequestQueueRepository(requestRepository);
    this.itemRepository = new ItemRepository(clients);
    this.userRepository = new UserRepository(clients);
    this.loanRepository = new LoanRepository(clients, itemRepository, userRepository);
    this.patronNoticePolicyRepository = new PatronNoticePolicyRepository(clients);
    this.patronGroupRepository = new PatronGroupRepository(clients);
    this.settingsRepository = new SettingsRepository(clients);
    this.loanPolicyRepository = new LoanPolicyRepository(clients);
    this.overdueFinePolicyRepository = new OverdueFinePolicyRepository(clients);
    this.lostItemPolicyRepository = new LostItemPolicyRepository(clients);
    this.configurationRepository = new ConfigurationRepository(clients);
  }
}
