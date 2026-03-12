package com.example.importer.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class JwtRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String RESOURCE_ACCESS = "resource_access";
    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String API_CLIENT = "spring-api";
    private static final String ROLE_PREFIX = "ROLE_";
    private static final String SCOPE_PREFIX = "SCOPE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        System.out.println("JWT="+jwt);
        Set<GrantedAuthority> authorities = new HashSet<>();
        extractRealmRoles(jwt).forEach(role -> authorities.add(new SimpleGrantedAuthority(withRolePrefix(role))));
        extractClientRoles(jwt).forEach(role -> authorities.add(new SimpleGrantedAuthority(withScopePrefix(role))));
        return authorities;
    }

    private Collection<String> extractRealmRoles(Jwt jwt) {
        System.out.println("JWT="+jwt);
        Object realmAccess = jwt.getClaim(REALM_ACCESS);
        if (!(realmAccess instanceof Map<?, ?> realm)) {
            return Set.of();
        }
        Object roles = realm.get(ROLES);
        if (!(roles instanceof Collection<?> rawRoles)) {
            return Set.of();
        }
        return rawRoles.stream().map(Object::toString).collect(Collectors.toSet());
    }

    private Collection<String> extractClientRoles(Jwt jwt) {
        Object resourceAccess = jwt.getClaim(RESOURCE_ACCESS);
        if (!(resourceAccess instanceof Map<?, ?> resources)) {
            return Set.of();
        }
        Object client = resources.get(API_CLIENT);
        if (!(client instanceof Map<?, ?> clientMap)) {
            return Set.of();
        }
        Object roles = clientMap.get(ROLES);
        if (!(roles instanceof Collection<?> rawRoles)) {
            return Set.of();
        }
        return rawRoles.stream().map(Object::toString).collect(Collectors.toSet());
    }

    private String withRolePrefix(String role) {
        return role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
    }

    private String withScopePrefix(String role) {
        return role.startsWith(SCOPE_PREFIX) ? role : SCOPE_PREFIX + role;
    }
}
