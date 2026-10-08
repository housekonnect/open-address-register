package org.ugaddress.register.shared.internal;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for validating access tokens issued by Authentik.
 *
 * @param jwkSetUri URL of the signing keys, reachable from the backend (may be an internal host name)
 * @param issuers accepted {@code iss} values; Authentik issues one per application and per public host name
 * @param audiences accepted {@code aud} values (client ids of the register's OIDC applications)
 */
@ConfigurationProperties("register.security")
record SecurityProperties(String jwkSetUri, List<String> issuers, List<String> audiences) {

    SecurityProperties {
        issuers = List.copyOf(issuers);
        audiences = List.copyOf(audiences);
    }
}
