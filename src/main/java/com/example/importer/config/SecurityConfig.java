//package com.example.commandservice.config;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.convert.converter.Converter;
//import org.springframework.http.HttpMethod;
//import org.springframework.security.authentication.AbstractAuthenticationToken;
//import org.springframework.security.config.Customizer;
//import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
//import org.springframework.security.config.web.server.ServerHttpSecurity;
//import org.springframework.security.oauth2.core.OAuth2TokenValidator;
//import org.springframework.security.oauth2.jwt.Jwt;
//import org.springframework.security.oauth2.jwt.JwtValidators;
//import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
//import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
//import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
//import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
//import org.springframework.security.web.server.SecurityWebFilterChain;
//import reactor.core.publisher.Mono;
//
//@Configuration
//@EnableReactiveMethodSecurity
//public class SecurityConfig {
//
//    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
//    private String jwkSetUri;
//
//    @Value("${app.security.expected-issuer}")
//    private String expectedIssuer;
//
//    @Bean
//    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
//        http
//                .csrf(ServerHttpSecurity.CsrfSpec::disable)
//                .cors(Customizer.withDefaults())
//                .authorizeExchange(exchanges -> exchanges
//                        // CORS preflight
//                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
//
//                        // Public: swagger, health, UI, assets Vite
//                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
//                        .pathMatchers("/actuator/health", "/actuator/info").permitAll()
//                        .pathMatchers("/command/auth/**").permitAll()
//                        .pathMatchers(
//                                "/ui/**",
//                                "/@vite/**",
//                                "/@react-refresh/**",
//                                "/assets/**",
//                                "/favicon.ico",
//                                "/node_modules/**",
//                                "/src/**",
//                                "/@react-refresh",
//                                "/favicon.ico"
//                        ).permitAll()
////                        - Path=/@vite/**, /@react-refresh, /@react-refresh/**, /src/**, /node_modules/**, /assets/**, /favicon.ico
//
////                        .pathMatchers("**").permitAll()
//                        // Backend: trebuie token
////                           .pathMatchers("/command/**").authenticated()
//
////                        .pathMatchers("/command/**", "/query/**", "/server/**").authenticated()
////                        .pathMatchers("/command/**", "/query/**", "/server/**").authenticated()
//
//                        // orice altceva – cere autentificare
//                        .anyExchange().authenticated()
//                )
//                .oauth2ResourceServer(oauth2 -> oauth2
//                        .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter()))
//                );
//
//        return http.build();
//    }
//
//    @Bean
//    public ReactiveJwtDecoder reactiveJwtDecoder() {
//
//
//        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
//        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(expectedIssuer);
//        System.out.println("jwkSetUri:=== " + jwkSetUri);
//        System.out.println("expectedUri:=== " + expectedIssuer);
//
//        System.out.println("withIssuer:=## " + withIssuer);
//
//        decoder.setJwtValidator(withIssuer);
//        return decoder;
//    }
//
//    @Bean
//    public Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveJwtAuthenticationConverter() {
//        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
//        // folosesti convertorul tau de roluri
//        delegate.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());
//        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
//    }
//
//
//}

package com.example.importer.config;
import com.example.importer.config.JwtRoleConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.convert.converter.Converter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${app.security.expected-issuer}")
    private String expectedIssuer;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth

                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public: swagger, actuator, auth
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                "/actuator/info"
//                                "/import/**"
                        ).permitAll()

                        .requestMatchers(
                                "/ui/**",
                                "/assets/**",
                                "/favicon.ico"
                        ).permitAll()

                        // orice altceva – necesită token
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    // 🔐 JWT Decoder (servlet)
    @Bean
    public JwtDecoder jwtDecoder() {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        OAuth2TokenValidator<Jwt> withIssuer =
                JwtValidators.createDefaultWithIssuer(expectedIssuer);

        decoder.setJwtValidator(withIssuer);
        return decoder;
    }

    // 🔄 JWT → Authentication (roles)

    // 🔄 JWT → Authentication (roles)
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtRoleConverter());
        return converter;
    }
}
