package com.example.importer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "keycloak";

    @Value("${app.security.expected-issuer}")
    private String issuer;

    @Bean
    public OpenAPI openAPI() {
        String oidc = issuer + "/protocol/openid-connect";
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                        .authorizationUrl(oidc + "/auth")
                                        .tokenUrl(oidc + "/token")
                                        .scopes(new Scopes()
                                                .addString("openid", "OpenID")
                                                .addString("profile", "Profile"))))))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }
}
