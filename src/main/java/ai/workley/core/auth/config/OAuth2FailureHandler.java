package ai.workley.core.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
public class OAuth2FailureHandler implements ServerAuthenticationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2FailureHandler.class);

    private final String frontendBaseUrl;

    public OAuth2FailureHandler(@Value("${gateway.frontend.base-url:}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        log.warn("OAuth2 authentication failed", exception);
        webFilterExchange.getExchange().getResponse().getHeaders().setLocation(URI.create(frontendBaseUrl + "/?auth=error"));
        webFilterExchange.getExchange().getResponse().setStatusCode(org.springframework.http.HttpStatus.FOUND);
        return webFilterExchange.getExchange().getResponse().setComplete();
    }
}