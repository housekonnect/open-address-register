package org.ugaddress.register.shared.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * HTTP caching for the API: every successful GET carries an ETag and honours {@code If-None-Match}.
 *
 * <p>Responses differ by authorization (partners see more), so they also vary on {@code Authorization}.
 */
@Configuration(proxyBeanMethods = false)
class WebConfig {

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        final FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
            new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/v1/*");
        return registration;
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> varyFilter() {
        final FilterRegistrationBean<OncePerRequestFilter> registration =
            new FilterRegistrationBean<>(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
                                                final FilterChain chain) throws ServletException, IOException {
                    response.addHeader(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION);
                    chain.doFilter(request, response);
                }
            });
        registration.addUrlPatterns("/v1/*");
        return registration;
    }
}
