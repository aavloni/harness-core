package io.harness.telemetry.helpers;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class InstrumentationHelper {
  public String getUserId() {
    Principal principal = SecurityContextBuilder.getPrincipal();
    String identity = null;
    if (principal != null) {
      switch (principal.getType()) {
        case USER:
          UserPrincipal userPrincipal = (UserPrincipal) principal;
          identity = userPrincipal.getEmail();
          break;
        case SERVICE_ACCOUNT:
          ServiceAccountPrincipal serviceAccountPrincipal = (ServiceAccountPrincipal) principal;
          identity = serviceAccountPrincipal.getEmail();
          break;
        case API_KEY:
        case SERVICE:
          identity = principal.getName();
          break;
        default:
          log.warn("Unknown principal type from SecurityContextBuilder when reading identity");
      }
    }
    if (isEmpty(identity)) {
      log.debug("Failed to read identity from principal, use system user instead");
      identity = "system";
    }
    return identity;
  }
}
