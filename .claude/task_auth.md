# Authentication & Authorization Implementation Plan

**Project**: AiHelperBot (com.solbeg.sas.perfmgmnt)
**Stack**: Spring Boot 3.2.5, Java 17, PostgreSQL, Liquibase, Spring Security

---

## 1. Current Security State

The existing `SecurityConfig` is minimal -- it disables CSRF and permits all requests unconditionally:

```java
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

`SecurityUtils` reads the current principal name from `SecurityContextHolder` (returns `null` for anonymous). It is used by `BotFeedbackServiceImpl` to populate `employeeEmail` on feedback records. There is no user entity, no authentication mechanism, and no role-based access control.

Spring Security starter is already on the classpath. `spring-security-test` is present for tests.

---

## 2. Maven Dependencies to Add

Add to `pom.xml` `<dependencies>`:

```xml
<!-- OAuth2 Client (Google, GitHub login) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>

<!-- JJWT (JWT creation and parsing) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

Add JJWT version property:

```xml
<jjwt.version>0.12.6</jjwt.version>
```

No additional springdoc dependencies needed -- `springdoc-openapi-starter-webmvc-ui:2.5.0` is already present.

---

## 3. Liquibase Migrations

New file: `src/main/resources/db/changelog/changes/002-create-auth-tables.yaml`

```yaml
databaseChangeLog:
  # --- app_user table ---
  - changeSet:
      id: 002-create-app-user-seq
      author: migrations
      changes:
        - createSequence:
            sequenceName: app_user_seq
            startValue: 1
            incrementBy: 50

  - changeSet:
      id: 002-create-app-user-table
      author: migrations
      changes:
        - createTable:
            tableName: app_user
            columns:
              - column:
                  name: id
                  type: BIGINT
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: email
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
                    unique: true
              - column:
                  name: password
                  type: VARCHAR(255)
                  remarks: "BCrypt hash. NULL for OAuth2-only users."
              - column:
                  name: name
                  type: VARCHAR(255)
              - column:
                  name: auth_provider
                  type: VARCHAR(50)
                  defaultValue: LOCAL
                  constraints:
                    nullable: false
                  remarks: "LOCAL, GOOGLE, GITHUB, etc."
              - column:
                  name: provider_id
                  type: VARCHAR(255)
                  remarks: "External provider subject ID. NULL for LOCAL users."
              - column:
                  name: created_at
                  type: TIMESTAMP
                  defaultValueComputed: CURRENT_TIMESTAMP
              - column:
                  name: updated_at
                  type: TIMESTAMP
                  defaultValueComputed: CURRENT_TIMESTAMP

  # --- role table ---
  - changeSet:
      id: 002-create-role-table
      author: migrations
      changes:
        - createTable:
            tableName: role
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: name
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
                    unique: true

  - changeSet:
      id: 002-seed-roles
      author: migrations
      changes:
        - insert:
            tableName: role
            columns:
              - column: { name: name, value: ROLE_USER }
              - column: { name: name, value: ROLE_ADMIN }

  # --- user_roles join table ---
  - changeSet:
      id: 002-create-user-roles-table
      author: migrations
      changes:
        - createTable:
            tableName: user_roles
            columns:
              - column:
                  name: user_id
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: role_id
                  type: BIGINT
                  constraints:
                    nullable: false
        - addPrimaryKey:
            tableName: user_roles
            columnNames: user_id, role_id
            constraintName: pk_user_roles
        - addForeignKeyConstraint:
            baseTableName: user_roles
            baseColumnNames: user_id
            referencedTableName: app_user
            referencedColumnNames: id
            constraintName: fk_user_roles_user
            onDelete: CASCADE
        - addForeignKeyConstraint:
            baseTableName: user_roles
            baseColumnNames: role_id
            referencedTableName: role
            referencedColumnNames: id
            constraintName: fk_user_roles_role
            onDelete: CASCADE
```

Register in `db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - include:
      file: db/changelog/changes/001-create-bot-feedback.yaml
  - include:
      file: db/changelog/changes/002-create-auth-tables.yaml
```

---

## 4. JPA Entities

### 4a. `UserRole` Enum

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/model/UserRole.java`

```java
package com.solbeg.sas.perfmgmnt.model;

public enum UserRole {
    ROLE_USER,
    ROLE_ADMIN
}
```

### 4b. `AuthProvider` Enum

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/model/AuthProvider.java`

```java
package com.solbeg.sas.perfmgmnt.model;

public enum AuthProvider {
    LOCAL,
    GOOGLE,
    GITHUB
}
```

### 4c. `Role` Entity

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/model/Role.java`

```java
package com.solbeg.sas.perfmgmnt.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 50)
    private UserRole name;

    public Role(UserRole name) {
        this.name = name;
    }
}
```

### 4d. `AppUser` Entity

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/model/AppUser.java`

```java
package com.solbeg.sas.perfmgmnt.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_user_seq_gen")
    @SequenceGenerator(name = "app_user_seq_gen", sequenceName = "app_user_seq")
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    private String providerId;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

**Design note**: `FetchType.EAGER` on roles is acceptable here because users typically have 1-2 roles, and roles are always needed for security decisions.

---

## 5. Repositories

### 5a. `AppUserRepository`

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/repository/AppUserRepository.java`

```java
package com.solbeg.sas.perfmgmnt.repository;

import com.solbeg.sas.perfmgmnt.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
```

### 5b. `RoleRepository`

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/repository/RoleRepository.java`

```java
package com.solbeg.sas.perfmgmnt.repository;

import com.solbeg.sas.perfmgmnt.model.Role;
import com.solbeg.sas.perfmgmnt.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(UserRole name);
}
```

---

## 6. JWT Utility Class

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/security/JwtTokenProvider.java`

```java
package com.solbeg.sas.perfmgmnt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return parseClaims(token).get("roles", List.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token expired: {}", ex.getMessage());
        } catch (JwtException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

---

## 7. JwtAuthenticationFilter

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/security/JwtAuthenticationFilter.java`

```java
package com.solbeg.sas.perfmgmnt.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String email = jwtTokenProvider.getEmailFromToken(token);
            List<String> roles = jwtTokenProvider.getRolesFromToken(token);

            List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            var authentication = new UsernamePasswordAuthenticationToken(
                    email, null, authorities);
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
```

---

## 8. UserDetailsService Implementation

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/security/AppUserDetailsService.java`

```java
package com.solbeg.sas.perfmgmnt.security;

import com.solbeg.sas.perfmgmnt.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var appUser = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with email: " + email));

        var authorities = appUser.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .toList();

        return new User(appUser.getEmail(), appUser.getPassword() != null ? appUser.getPassword() : "", authorities);
    }
}
```

---

## 9. Spring Security Configuration

**File**: Replace `src/main/java/com/solbeg/sas/perfmgmnt/config/SecurityConfig.java`

```java
package com.solbeg.sas.perfmgmnt.config;

import com.solbeg.sas.perfmgmnt.security.JwtAuthenticationFilter;
import com.solbeg.sas.perfmgmnt.security.OAuth2AuthenticationSuccessHandler;
import com.solbeg.sas.perfmgmnt.security.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public auth endpoints
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Public bot topics endpoint (intro text)
                .requestMatchers(HttpMethod.GET, "/api/v1/bot/topics").permitAll()
                // Swagger / OpenAPI
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**",
                    "/swagger-resources/**",
                    "/webjars/**"
                ).permitAll()
                // Actuator health
                .requestMatchers("/actuator/health").permitAll()
                // OAuth2 endpoints
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo ->
                    userInfo.userService(customOAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
```

---

## 10. OAuth2 Components

### 10a. `CustomOAuth2UserService`

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/security/CustomOAuth2UserService.java`

This service intercepts the OAuth2 user info response and creates/updates the local `AppUser` record. The key design point: it delegates to a **provider-specific attribute extractor** so that adding a new provider does not require modifying this class.

```java
package com.solbeg.sas.perfmgmnt.security;

import com.solbeg.sas.perfmgmnt.model.AppUser;
import com.solbeg.sas.perfmgmnt.model.AuthProvider;
import com.solbeg.sas.perfmgmnt.model.UserRole;
import com.solbeg.sas.perfmgmnt.repository.AppUserRepository;
import com.solbeg.sas.perfmgmnt.repository.RoleRepository;
import com.solbeg.sas.perfmgmnt.security.oauth2.OAuth2UserInfo;
import com.solbeg.sas.perfmgmnt.security.oauth2.OAuth2UserInfoFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.create(registrationId, oAuth2User.getAttributes());
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        appUserRepository.findByEmail(userInfo.email())
                .ifPresentOrElse(
                        existingUser -> {
                            existingUser.setName(userInfo.name());
                            existingUser.setAuthProvider(provider);
                            existingUser.setProviderId(userInfo.id());
                        },
                        () -> {
                            var role = roleRepository.findByName(UserRole.ROLE_USER)
                                    .orElseThrow(() -> new IllegalStateException("ROLE_USER not found in DB"));
                            var newUser = new AppUser();
                            newUser.setEmail(userInfo.email());
                            newUser.setName(userInfo.name());
                            newUser.setAuthProvider(provider);
                            newUser.setProviderId(userInfo.id());
                            newUser.setRoles(Set.of(role));
                            appUserRepository.save(newUser);
                        }
                );

        return oAuth2User;
    }
}
```

### 10b. `OAuth2UserInfo` Record and Factory

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/security/oauth2/OAuth2UserInfo.java`

```java
package com.solbeg.sas.perfmgmnt.security.oauth2;

/**
 * Normalized user info extracted from an OAuth2 provider response.
 */
public record OAuth2UserInfo(
        String id,
        String name,
        String email
) {}
```

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/security/oauth2/OAuth2UserInfoFactory.java`

```java
package com.solbeg.sas.perfmgmnt.security.oauth2;

import java.util.Map;

/**
 * Factory that produces a normalized {@link OAuth2UserInfo} from provider-specific
 * attribute maps. To add a new OAuth2 provider, add a new case to the switch expression.
 */
public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {}

    public static OAuth2UserInfo create(String registrationId, Map<String, Object> attributes) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> new OAuth2UserInfo(
                    (String) attributes.get("sub"),
                    (String) attributes.get("name"),
                    (String) attributes.get("email")
            );
            case "github" -> new OAuth2UserInfo(
                    String.valueOf(attributes.get("id")),
                    (String) attributes.get("name"),
                    (String) attributes.get("email")
            );
            default -> throw new IllegalArgumentException(
                    "Unsupported OAuth2 provider: " + registrationId);
        };
    }
}
```

### 10c. `OAuth2AuthenticationSuccessHandler`

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/security/OAuth2AuthenticationSuccessHandler.java`

After OAuth2 login completes, this handler creates an internal JWT token and redirects the client with the token as a query parameter.

```java
package com.solbeg.sas.perfmgmnt.security;

import com.solbeg.sas.perfmgmnt.model.AppUser;
import com.solbeg.sas.perfmgmnt.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final AppUserRepository appUserRepository;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        AppUser appUser = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "User not found after OAuth2 login: " + email));

        var roles = appUser.getRoles().stream()
                .map(role -> role.getName().name())
                .toList();

        String token = jwtTokenProvider.generateToken(email, roles);

        String targetUrl = redirectUri + "?token=" + token;
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
```

---

## 11. Auth DTOs

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/dto/request/RegisterRequest.java`

```java
package com.solbeg.sas.perfmgmnt.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be 8-100 characters")
        String password,

        @Size(max = 255, message = "Name too long")
        String name
) {}
```

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/dto/request/LoginRequest.java`

```java
package com.solbeg.sas.perfmgmnt.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {}
```

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/dto/response/AuthResponse.java`

```java
package com.solbeg.sas.perfmgmnt.dto.response;

public record AuthResponse(
        String token,
        String email,
        String tokenType
) {
    public AuthResponse(String token, String email) {
        this(token, email, "Bearer");
    }
}
```

---

## 12. Auth REST Controller

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/controller/AuthController.java`

```java
package com.solbeg.sas.perfmgmnt.controller;

import com.solbeg.sas.perfmgmnt.dto.request.LoginRequest;
import com.solbeg.sas.perfmgmnt.dto.request.RegisterRequest;
import com.solbeg.sas.perfmgmnt.dto.response.AuthResponse;
import com.solbeg.sas.perfmgmnt.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user", security = @SecurityRequirement(name = ""))
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(summary = "Login with email and password", security = @SecurityRequirement(name = ""))
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
```

---

## 13. Auth Service

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/service/AuthService.java`

```java
package com.solbeg.sas.perfmgmnt.service;

import com.solbeg.sas.perfmgmnt.dto.request.LoginRequest;
import com.solbeg.sas.perfmgmnt.dto.request.RegisterRequest;
import com.solbeg.sas.perfmgmnt.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
```

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/service/AuthServiceImpl.java`

```java
package com.solbeg.sas.perfmgmnt.service;

import com.solbeg.sas.perfmgmnt.dto.request.LoginRequest;
import com.solbeg.sas.perfmgmnt.dto.request.RegisterRequest;
import com.solbeg.sas.perfmgmnt.dto.response.AuthResponse;
import com.solbeg.sas.perfmgmnt.exceptionhandler.ErrorCodes;
import com.solbeg.sas.perfmgmnt.exceptionhandler.exception.RestException;
import com.solbeg.sas.perfmgmnt.model.AppUser;
import com.solbeg.sas.perfmgmnt.model.AuthProvider;
import com.solbeg.sas.perfmgmnt.model.UserRole;
import com.solbeg.sas.perfmgmnt.repository.AppUserRepository;
import com.solbeg.sas.perfmgmnt.repository.RoleRepository;
import com.solbeg.sas.perfmgmnt.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (appUserRepository.existsByEmail(request.email())) {
            throw new RestException(ErrorCodes.EMAIL_ALREADY_EXISTS);
        }

        var role = roleRepository.findByName(UserRole.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found in DB"));

        var user = new AppUser();
        user.setEmail(request.email().toLowerCase());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRoles(Set.of(role));

        appUserRepository.save(user);

        var roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .toList();
        String token = jwtTokenProvider.generateToken(user.getEmail(), roles);

        return new AuthResponse(token, user.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        var user = appUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new RestException(ErrorCodes.BAD_CREDENTIALS));

        var roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .toList();
        String token = jwtTokenProvider.generateToken(user.getEmail(), roles);

        return new AuthResponse(token, user.getEmail());
    }
}
```

---

## 14. New Error Codes

Update `ErrorCodes` enum to add auth-related error codes:

```java
package com.solbeg.sas.perfmgmnt.exceptionhandler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCodes {

    BOT_FEEDBACK_NOT_FOUND("300000", "Bot feedback not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("400001", "Email is already registered", HttpStatus.CONFLICT),
    BAD_CREDENTIALS("400002", "Invalid email or password", HttpStatus.UNAUTHORIZED);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
```

Also add to `GlobalExceptionHandler`:

```java
@ExceptionHandler(BadCredentialsException.class)
public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("errorCode", ErrorCodes.BAD_CREDENTIALS.getCode());
    body.put("message", ErrorCodes.BAD_CREDENTIALS.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
}

@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("message", "Access denied");
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
}
```

---

## 15. AdminSeeder (Startup Initialization)

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/security/AdminSeeder.java`

```java
package com.solbeg.sas.perfmgmnt.security;

import com.solbeg.sas.perfmgmnt.model.AppUser;
import com.solbeg.sas.perfmgmnt.model.AuthProvider;
import com.solbeg.sas.perfmgmnt.model.UserRole;
import com.solbeg.sas.perfmgmnt.repository.AppUserRepository;
import com.solbeg.sas.perfmgmnt.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.name:Admin}")
    private String adminName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appUserRepository.existsByEmail(adminEmail)) {
            log.info("Admin account already exists: {}", adminEmail);
            return;
        }

        var adminRole = roleRepository.findByName(UserRole.ROLE_ADMIN)
                .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found in DB"));
        var userRole = roleRepository.findByName(UserRole.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER not found in DB"));

        var admin = new AppUser();
        admin.setEmail(adminEmail.toLowerCase());
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setName(adminName);
        admin.setAuthProvider(AuthProvider.LOCAL);
        admin.setRoles(Set.of(adminRole, userRole));

        appUserRepository.save(admin);
        log.info("Admin account created: {}", adminEmail);
    }
}
```

---

## 16. Application Properties Updates

Add to `application.yml`:

```yaml
app:
  jwt:
    # Base64-encoded 256-bit secret (generate via: openssl rand -base64 32)
    secret: ${JWT_SECRET}
    expiration-ms: 86400000  # 24 hours
  admin:
    email: ${ADMIN_EMAIL:admin@solbeg.com}
    password: ${ADMIN_PASSWORD}
    name: ${ADMIN_NAME:Admin}
  oauth2:
    redirect-uri: ${OAUTH2_REDIRECT_URI:http://localhost:3000/oauth2/callback}

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: email, profile
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: user:email, read:user
```

---

## 17. Swagger/OpenAPI Configuration

**File**: `src/main/java/com/solbeg/sas/perfmgmnt/config/OpenApiConfig.java`

```java
package com.solbeg.sas.perfmgmnt.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "AI Helper Bot API",
                version = "v1",
                description = "Performance Management RAG Chatbot API"
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter your JWT token (without the 'Bearer' prefix)"
)
public class OpenApiConfig {
}
```

### Marking public endpoints

Public endpoints (auth, topics) must explicitly opt out of the global security requirement using `@Operation(security = @SecurityRequirement(name = ""))`. Example already shown in `AuthController` above. Apply the same to `BotTopicsController.getIntroAndTopics()`:

```java
@Operation(summary = "Get bot intro text and available topics",
           security = @SecurityRequirement(name = ""))
```

---

## 18. Role-Based Access for Existing Endpoints

After authentication is enforced, apply method-level security where appropriate:

| Endpoint | Access |
|---|---|
| `POST /api/v1/auth/register` | Public |
| `POST /api/v1/auth/login` | Public |
| `GET /api/v1/bot/topics` | Public |
| `POST /api/v1/ask` | Authenticated (USER or ADMIN) |
| `POST /api/v1/botfeedback` | Authenticated (USER or ADMIN) |
| `GET /api/v1/botfeedback/{id}` | ADMIN only |

For the admin-only endpoint, add `@PreAuthorize`:

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/{id}")
public ResponseEntity<BotFeedbackResponse> getBotFeedbackById(@PathVariable Long id) { ... }
```

**Note**: `hasRole('ADMIN')` automatically matches `ROLE_ADMIN` authority. The `@EnableMethodSecurity` annotation on `SecurityConfig` enables `@PreAuthorize`.

---

## 19. Update SecurityUtils

`SecurityUtils.getUserName()` continues to work as-is because the `JwtAuthenticationFilter` sets the principal to the email string. No changes required.

---

## 20. Test Profile Updates

In `application-test.yml`, add:

```yaml
app:
  jwt:
    secret: dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2g=
    expiration-ms: 3600000
  admin:
    email: admin@test.com
    password: admin-test-password
    name: Test Admin

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: test-google-client-id
            client-secret: test-google-client-secret
          github:
            client-id: test-github-client-id
            client-secret: test-github-client-secret
```

For integration tests, consider disabling `AdminSeeder` via a `@ConditionalOnProperty` or by setting `app.admin.enabled=false` in test profile.

---

## 21. Scalability: Adding New OAuth2 Providers

To add a new OAuth2 provider (e.g., Microsoft, GitLab, Okta), only three changes are needed:

1. **Add enum value** to `AuthProvider`:
   ```java
   MICROSOFT
   ```

2. **Add case** to `OAuth2UserInfoFactory.create()`:
   ```java
   case "microsoft" -> new OAuth2UserInfo(
       (String) attributes.get("sub"),
       (String) attributes.get("displayName"),
       (String) attributes.get("mail")
   );
   ```

3. **Add client registration** in `application.yml`:
   ```yaml
   spring.security.oauth2.client.registration.microsoft:
     client-id: ${MICROSOFT_CLIENT_ID}
     client-secret: ${MICROSOFT_CLIENT_SECRET}
     scope: openid, email, profile
   ```

No changes to `SecurityConfig`, `CustomOAuth2UserService`, `OAuth2AuthenticationSuccessHandler`, or any other class. The architecture is open for extension, closed for modification.

---

## 22. Complete New File Inventory

| # | File Path | Type |
|---|---|---|
| 1 | `pom.xml` | Modify (add JJWT, OAuth2 client deps) |
| 2 | `src/main/resources/application.yml` | Modify (add JWT, admin, OAuth2 config) |
| 3 | `src/main/resources/db/changelog/changes/002-create-auth-tables.yaml` | New |
| 4 | `src/main/resources/db/changelog/db.changelog-master.yaml` | Modify (add 002 include) |
| 5 | `src/main/java/.../model/UserRole.java` | New |
| 6 | `src/main/java/.../model/AuthProvider.java` | New |
| 7 | `src/main/java/.../model/Role.java` | New |
| 8 | `src/main/java/.../model/AppUser.java` | New |
| 9 | `src/main/java/.../repository/AppUserRepository.java` | New |
| 10 | `src/main/java/.../repository/RoleRepository.java` | New |
| 11 | `src/main/java/.../security/JwtTokenProvider.java` | New |
| 12 | `src/main/java/.../security/JwtAuthenticationFilter.java` | New |
| 13 | `src/main/java/.../security/AppUserDetailsService.java` | New |
| 14 | `src/main/java/.../security/CustomOAuth2UserService.java` | New |
| 15 | `src/main/java/.../security/oauth2/OAuth2UserInfo.java` | New |
| 16 | `src/main/java/.../security/oauth2/OAuth2UserInfoFactory.java` | New |
| 17 | `src/main/java/.../security/OAuth2AuthenticationSuccessHandler.java` | New |
| 18 | `src/main/java/.../security/AdminSeeder.java` | New |
| 19 | `src/main/java/.../dto/request/RegisterRequest.java` | New |
| 20 | `src/main/java/.../dto/request/LoginRequest.java` | New |
| 21 | `src/main/java/.../dto/response/AuthResponse.java` | New |
| 22 | `src/main/java/.../controller/AuthController.java` | New |
| 23 | `src/main/java/.../service/AuthService.java` | New |
| 24 | `src/main/java/.../service/AuthServiceImpl.java` | New |
| 25 | `src/main/java/.../config/SecurityConfig.java` | Replace |
| 26 | `src/main/java/.../config/OpenApiConfig.java` | New |
| 27 | `src/main/java/.../exceptionhandler/ErrorCodes.java` | Modify (add auth errors) |
| 28 | `src/main/java/.../exceptionhandler/GlobalExceptionHandler.java` | Modify (add auth handlers) |
| 29 | `src/main/java/.../controller/BotTopicsController.java` | Modify (add public security annotation) |
| 30 | `src/main/java/.../controller/BotFeedbackController.java` | Modify (add @PreAuthorize on GET) |

---

## 23. Dependency Graph

```
AuthController
  -> AuthService
       -> AppUserRepository
       -> RoleRepository
       -> PasswordEncoder
       -> JwtTokenProvider
       -> AuthenticationManager
            -> AppUserDetailsService
                 -> AppUserRepository

JwtAuthenticationFilter (filter chain)
  -> JwtTokenProvider

OAuth2 login flow:
  CustomOAuth2UserService
    -> OAuth2UserInfoFactory
    -> AppUserRepository
    -> RoleRepository
  OAuth2AuthenticationSuccessHandler
    -> JwtTokenProvider
    -> AppUserRepository

AdminSeeder (startup)
  -> AppUserRepository
  -> RoleRepository
  -> PasswordEncoder
```

---

## 24. Implementation Order (Recommended)

1. Add Maven dependencies to `pom.xml`
2. Create Liquibase migration `002-create-auth-tables.yaml` and update master
3. Create enums: `UserRole`, `AuthProvider`
4. Create entities: `Role`, `AppUser`
5. Create repositories: `RoleRepository`, `AppUserRepository`
6. Create `JwtTokenProvider`
7. Create `JwtAuthenticationFilter`
8. Create `AppUserDetailsService`
9. Create DTOs: `RegisterRequest`, `LoginRequest`, `AuthResponse`
10. Create `AuthService` / `AuthServiceImpl`
11. Create `AuthController`
12. Create OAuth2 components: `OAuth2UserInfo`, `OAuth2UserInfoFactory`, `CustomOAuth2UserService`, `OAuth2AuthenticationSuccessHandler`
13. Replace `SecurityConfig`
14. Create `AdminSeeder`
15. Create `OpenApiConfig`
16. Update `ErrorCodes` and `GlobalExceptionHandler`
17. Annotate existing controllers (public markers, `@PreAuthorize`)
18. Update `application.yml` and test config
19. Write tests
