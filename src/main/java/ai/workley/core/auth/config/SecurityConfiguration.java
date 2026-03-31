package ai.workley.core.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;

import java.util.List;

@Configuration
public class SecurityConfiguration {
    private final AnonymousJwtSecret jwtSecret;
    private final AuthenticatedJwtWebFilter authenticatedJwtWebFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    public SecurityConfiguration(
            AnonymousJwtSecret jwtSecret,
            OAuth2FailureHandler oAuth2FailureHandler,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            AuthenticatedJwtWebFilter authenticatedJwtWebFilter
    ) {
        this.jwtSecret = jwtSecret;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.oAuth2FailureHandler = oAuth2FailureHandler;
        this.authenticatedJwtWebFilter = authenticatedJwtWebFilter;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity serverHttpSecurity) {
        return serverHttpSecurity.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(
                                new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .oauth2Login(oauth2 ->
                        oauth2.authenticationSuccessHandler(oAuth2SuccessHandler)
                                .authenticationFailureHandler(oAuth2FailureHandler)
                )
                .anonymous((ServerHttpSecurity.AnonymousSpec anonymousSpec) ->
                        anonymousSpec.authenticationFilter(
                                new CookieAnonymousAuthenticationWebFilter(this.jwtSecret))
                )
                .addFilterBefore(authenticatedJwtWebFilter, SecurityWebFiltersOrder.ANONYMOUS_AUTHENTICATION)
                .authorizeExchange(withAuthorizeExchange())
                .build();
    }

    private static Customizer<ServerHttpSecurity.AuthorizeExchangeSpec> withAuthorizeExchange() {
        String[] endpointsWhitelist =
                List.of("/api/chats/**", "/api/attachments/**", "/api/auth/**", "/actuator/**", "/oauth2/**", "/login/oauth2/**")
                        .toArray(new String[0]);
        return (ServerHttpSecurity.AuthorizeExchangeSpec authorizeExchangeSpec) ->
                authorizeExchangeSpec
                        .pathMatchers(endpointsWhitelist)
                        .permitAll()
                        .anyExchange()
                        .authenticated();
    }
}
