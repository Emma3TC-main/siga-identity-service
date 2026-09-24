package utp.siga.identity.domain;

import java.util.*;
import utp.siga.identity.application.Api;

public interface IdentityStore {
  Optional<Account> byUsername(String name);

  Account account(UUID id, boolean lock);

  Set<String> permissions(UUID id);

  Api.User user(UUID id);

  List<Api.Role> roles();

  Api.Role role(UUID id);

  Api.Page users(int page, int size);

  void event(String type, UUID id);
}
