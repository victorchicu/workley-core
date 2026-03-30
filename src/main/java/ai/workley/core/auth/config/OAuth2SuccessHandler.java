package ai.workley.core.auth.config;

import ai.workley.core.auth.service.AuthenticationService;
import ai.workley.core.auth.service.SocialLoginService;
import ai.workley.core.auth.service.SocialLoginService.SocialLoginResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2SuccessHandler implements ServerAuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    private final SocialLoginService socialLoginService;
    private final AuthenticationService authenticationService;
    private final String frontendBaseUrl;

    public OAuth2SuccessHandler(SocialLoginService socialLoginService,
                                AuthenticationService authenticationService,
                                @Value("${gateway.frontend.base-url:}") String frontendBaseUrl) {
        this.socialLoginService = socialLoginService;
        this.authenticationService = authenticationService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;

        String provider = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User oauthUser = oauthToken.getPrincipal();

        String email = oauthUser.getAttribute("email");
        String subject = oauthUser.getName();
        String fullName = extractFullName(oauthUser);
        Boolean emailVerified = oauthUser.getAttribute("email_verified");

        return socialLoginService.authenticate(provider, subject, email, Boolean.TRUE.equals(emailVerified), fullName)
                .flatMap(result ->
                        authenticationService.issueTokens(result.userId(), result.email(), webFilterExchange.getExchange().getResponse())
                                .thenReturn(result)
                )
                .flatMap(result ->
                        redirect(webFilterExchange,
                                buildRedirectUri(result, fullName))
                )
                .onErrorResume(e -> {
                    log.error("OAuth2 social login failed for provider={}, email={}", provider, email, e);
                    return redirect(webFilterExchange, frontendBaseUrl + "/?auth=error");
                });
    }

    private Mono<Void> redirect(WebFilterExchange webFilterExchange, String uri) {
        webFilterExchange.getExchange().getResponse().getHeaders().setLocation(URI.create(uri));
        webFilterExchange.getExchange().getResponse().setStatusCode(org.springframework.http.HttpStatus.FOUND);
        return webFilterExchange.getExchange().getResponse().setComplete();
    }

    private String extractFullName(OAuth2User oauthUser) {
        String name = oauthUser.getAttribute("name");

        if (name != null && !name.isBlank()) return name;

        String givenName = oauthUser.getAttribute("given_name");
        String familyName = oauthUser.getAttribute("family_name");

        if (givenName != null) {
            return familyName != null
                    ? givenName + " " + familyName
                    : givenName;
        }

        return null;
    }

    private String buildRedirectUri(SocialLoginResult result, String fullName) {
        if ("authenticated".equals(result.nextStep())) {
            return frontendBaseUrl + "/?auth=success";
        }
        String uri = frontendBaseUrl + "/?auth=profile-needed";
        if (fullName != null && !fullName.isBlank()) {
            uri += "&name=" + URLEncoder.encode(fullName, StandardCharsets.UTF_8);
        }
        return uri;
    }
}