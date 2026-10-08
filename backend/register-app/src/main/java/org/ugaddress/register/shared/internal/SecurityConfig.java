package org.ugaddress.register.shared.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.ugaddress.register.shared.Role;

/**
 * The backend is an OAuth2 resource server. Reads are public; writes require a role from the token's groups.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(final HttpSecurity http, final ProblemSecurityHandlers problems,
                                    final Converter<Jwt, AbstractAuthenticationToken> jwtConverter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/change-requests").hasAuthority(Role.CUSTODIAN_EDITOR.authority())
                .requestMatchers("/v1/field/**").hasAuthority(Role.FIELD_VERIFIER.authority())
                .requestMatchers(HttpMethod.GET, "/v1/**").permitAll()
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(problems)
                .accessDeniedHandler(problems));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(final SecurityProperties properties) {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            claimIn("iss", properties.issuers(), jwt -> jwt.getIssuer() == null ? List.of() : List.of(jwt.getIssuer().toString())),
            claimIn("aud", properties.audiences(), Jwt::getAudience)));
        return decoder;
    }

    /**
     * Maps the {@code groups} claim to {@code GROUP_*} authorities and keeps the standard {@code SCOPE_*} ones.
     */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        return jwt -> {
            final Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            final List<String> groups = jwt.getClaimAsStringList("groups");
            if (groups != null) {
                for (final String group : groups) {
                    authorities.add(new SimpleGrantedAuthority(Role.AUTHORITY_PREFIX + group));
                }
            }
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    private static OAuth2TokenValidator<Jwt> claimIn(final String claim, final List<String> accepted,
                                                     final Function<Jwt, List<String>> values) {
        final OAuth2Error error = new OAuth2Error("invalid_token", "The " + claim + " claim is not accepted", null);
        return jwt -> values.apply(jwt).stream().anyMatch(accepted::contains)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(error);
    }
}
