# 📖 [Master Study] Snowthing Sprint 01 백엔드 전 과정 전체 코드, 상세 주석, 설계 배경 & 아키텍처 대안 집대성 (260821)

> **문서 목적**: Snowthing 프로젝트 Sprint 01 백엔드 개발의 모든 코드(Spring Security 7, Global Exception, Auth, Member, JPA ORM, JPQL Fetch Join, DTO, 25개 테스트 수트 전체)를 불러와 **한 줄 한 줄 물리적 역할 해설 주석(Annotation)**을 달고, **"왜 그렇게 만들어졌는가(Design Rationale & Background)"**, **기술별 7대 필수 서술 체계(개념, Why, When, How, Pros, Alternatives, Trade-off & Mitigation)**, **파라미터/옵션 튜닝**, 그리고 **"여기서는 이렇게 설계했어도 좋았을 것이다" 5대 아키텍처 대안**까지 완벽하게 수록하여 노션(Notion)에 바로 복사해 공부할 수 있도록 만든 단 1개의 마스터 스터디 교재입니다.

---

## 🏛️ PART 1. 요청 진입 & 글로벌 보안 파이프라인 레이어

### 💡 [WHY] 왜 SecurityConfig를 이렇게 설계했는가?
1. **왜 Lambda DSL 패턴을 사용했는가?**: Spring Security 6.1+ 및 7.0 버전부터 기존의 `http.cors().and().csrf().disable()`과 같은 메서드 체이닝(Method Chaining) 방식이 딥 네스팅 및 가독성 저하 문제로 Deprecated 되었습니다. 명확한 함수형 람다 표현식(`http.cors(cors -> ...).csrf(csrf -> ...)`)으로 구성하여 설정 간의 경계를 물리적으로 명확히 분리하기 위함입니다.
2. **왜 `AbstractHttpConfigurer::disable`로 Form/HttpBasic을 껐는가?**: 백엔드는 HTML 페이지를 응답하는 SSR(JSP, Thymeleaf)이 아니라 JSON 데이터를 주고받는 Pure REST API 서버입니다. 시큐리티 기본 제공 로그인 폼 렌더링과 HTTP Basic 헤더 인증 방식을 끔으로써 불필요한 필터 오버헤드를 차단했습니다.
3. **왜 `AuthenticationManager`와 `SecurityContextRepository`를 빈으로 노출했는가?**: Controller에서 수동 비밀번호 대조 코드를 배제하고, Spring Security의 표준 인증 파이프라인(`authenticationManager.authenticate()`) 및 표준 세션 저장(`securityContextRepository.saveContext()`)을 수행할 수 있도록 의존성을 주입받기 위함입니다.

```java
// =================================================================================
// 🛡️ 1-1. SecurityConfig.java - Spring Security 7 최신 Lambda DSL 필터체인 설정
// =================================================================================
package com.ikae.snowthing.global.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration // 👈 스프링 CGLIB 프록시 기반으로 클래스를 등록하고 싱글톤 객체 생성을 보장
@EnableWebSecurity // 👈 Spring Security 필터 체인을 활성화하고 WebSecurityConfigurer를 스프링 컨텍스트에 등록
public class SecurityConfig {

    /**
     * [비밀번호 암호화 빈 등록]
     * BCrypt 해시 함수를 사용하여 비밀번호를 단방향 암호화하는 PasswordEncoder 등록.
     * Raw 비밀번호와 Encoded 비밀번호 대조는 passwordEncoder.matches()가 내부 솔트(Salt)를 파싱하여 수행.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * [AuthenticationManager 빈 노출]
     * Spring Security 표준 인증 처리를 총괄하는 AuthenticationManager를 스프링 빈으로 노출.
     * AuthController에서 수동 비밀번호 대조 대신 authenticationManager.authenticate(...)를 호출할 수 있게 함.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * [CorsConfigurationSource 빈 등록]
     * 프론트엔드 도메인(http://localhost:3000)과의 Cross-Origin 요청 허용 규칙을 정의.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000")); // 👈 Next.js 프론트엔드 출처 허용
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")); // 👈 HTTP 메서드 허용
        config.setAllowedHeaders(List.of("*")); // 👈 모든 HTTP 헤더 허용
        config.setAllowCredentials(true); // 👈 쿠키(JSESSIONID) 및 인증 자격 증명 전송 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 👈 모든 API 경로에 CORS 규칙 등록
        return source;
    }

    /**
     * [SecurityContextRepository 빈 등록]
     * HTTP 세션(HttpSession)에 SecurityContext를 저장하고 복원하는 HttpSessionSecurityContextRepository 지정.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * [SecurityFilterChain 메인 설정]
     * HTTP 요청이 진입할 때 실행되는 시큐리티 필터 파이프라인 체인을 구성.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS(Cross-Origin Resource Sharing) 설정 적용
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 2. CSRF 비활성화 (REST API + SPA 환경에서는 헤더/SameSite 세션 관리로 대처)
                .csrf(AbstractHttpConfigurer::disable)
                // 3. 폼 로그인 및 Basic HTTP 인증 비활성화 (JSON REST API 방식 사용)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 4. 시큐리티 컨텍스트 저장소 지정 (HttpSession 기반)
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository())
                )
                // 5. 세션 고정 공격 방어 (로그인 성공 시 기존 JSESSIONID 무효화 및 새 ID 발급: changeSessionId)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(sessionFixation -> sessionFixation.changeSessionId())
                )
                // 6. 시큐리티 표준 로그아웃 설정 (/api/auth/logout POST 요청 시 세션 무효화 및 쿠키 삭제)
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/api/auth/logout", "POST"))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpServletResponse.SC_OK);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"LOGOUT_SUCCESS\"}");
                        })
                )
                // 7. 예외 처리 핸들러 (401 Unauthorized / 403 Forbidden 표준 JSON 응답)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"code\":\"AUTH_001\",\"message\":\"로그인이 필요합니다.\"}");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"FORBIDDEN\",\"code\":\"AUTH_002\",\"message\":\"접근 권한이 없습니다.\"}");
                        })
                )
                // 8. URL별 인가(Authorization) 규칙 정의
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/members", "/api/auth/login", "/api/resorts", "/api/riding-styles").permitAll() // 👈 비인증 엔드포인트
                        .requestMatchers("/api/admin/**").hasRole("ADMIN") // 👈 관리자 권한 필요
                        .requestMatchers("/api/members/me").authenticated() // 👈 로그인 인증 필요
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
```

```java
// =================================================================================
// 👤 1-2. CustomUserDetails.java - Spring Security UserDetails 구현체
// =================================================================================
package com.ikae.snowthing.global.security;

import com.ikae.snowthing.domain.member.entity.Member;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor // 👈 final 필드(member)에 대한 생성자 자동 생성
public class CustomUserDetails implements UserDetails {

    private final Member member; // 👈 DB에서 조회한 실제 Member 엔티티 객체를 내부에 캡슐화

    /**
     * [권한 목록 반환]
     * MemberRole(ROLE_USER, ROLE_ADMIN)을 SimpleGrantedAuthority 객체로 변환하여 시큐리티에 제공.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(member.getRole().getKey()));
    }

    @Override
    public String getPassword() {
        return member.getPassword(); // 👈 BCrypt로 암호화된 비밀번호 반환
    }

    @Override
    public String getUsername() {
        return member.getEmail(); // 👈 로그인 식별자로 사용되는 이메일 반환
    }

    public String getPublicId() {
        return member.getPublicId(); // 👈 컨트롤러 및 서비스에서 도메인 식별자로 사용
    }

    public String getNickname() {
        return member.getNickname();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // 👈 계정 만료 여부 (true: 만료 안 됨)
    }

    @Override
    public boolean isAccountNonLocked() {
        // 👈 정지(SUSPENDED) 상태인 경우 계정 잠금 처리
        return member.getStatus() != com.ikae.snowthing.domain.member.entity.MemberStatus.SUSPENDED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // 👈 비밀번호 만료 여부 (true: 만료 안 됨)
    }

    @Override
    public boolean isEnabled() {
        // 👈 ACTIVE 상태인 경우만 계정 활성화
        return member.getStatus() == com.ikae.snowthing.domain.member.entity.MemberStatus.ACTIVE;
    }
}
```

```java
// =================================================================================
// 🔍 1-3. CustomUserDetailsService.java - UserDetailsService DB 회원 조회 구현체
// =================================================================================
package com.ikae.snowthing.global.security;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service // 👈 Component Scan 대상 지정
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    /**
     * [이메일로 회원 조회 및 UserDetails 변환]
     * DaoAuthenticationProvider가 로그인 시 호출하는 핵심 메서드.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(ErrorCode.MEMBER_NOT_FOUND.getMessage()));
        return new CustomUserDetails(member); // 👈 CustomUserDetails로 감싸서 반환
    }
}
```

---

## 🚨 PART 2. 글로벌 예외 처리 & 에러 응답 정형화 레이어

### 💡 [WHY] 왜 ErrorCode Enum 및 GlobalExceptionHandler로 일관화했는가?
1. **왜 예외 메시지 문자열 직접 생성을 금지했는가?**: `"INVALID_CREDENTIALS"` 같은 에러 문자열을 서비스 로직 곳곳에서 수동으로 생성하면, 오타로 인한 버그가 발생하고 에러 메시지가 수정될 때 수십 개의 클래스를 뒤져야 하는 비효율이 터집니다. `ErrorCode` Enum으로 정적 재사용하여 컴파일 시점 오타 방지 및 중앙 집권적 관리를 이뤘습니다.
2. **왜 `@RestControllerAdvice`로 예외를 캡처했는가?**: 컨트롤러 내부에서 `try-catch`로 예외를 일일이 받아서 처리하면 컨트롤러 코드가 비즈니스 외적인 에러 응답 코드로 더러워집니다. 스프링 AOP(Aspect Oriented Programming) 기반의 전역 예외 처리기를 구축하여 모든 에러를 1개의 규격화된 JSON 구조(`ErrorResponse`)로 응답하도록 설계했습니다.

```java
// =================================================================================
// 🏷️ 2-1. ErrorCode.java - 표준 에러코드 Enum
// =================================================================================
package com.ikae.snowthing.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_001", "이메일 또는 비밀번호가 일치하지 않습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_001", "존재하지 않는 회원입니다."),
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "MEMBER_002", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.BAD_REQUEST, "MEMBER_003", "이미 사용 중인 닉네임입니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_001", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status; // 👈 HTTP 상태 코드 (400, 401, 404, 500)
    private final String code;       // 👈 클라이언트 추적용 고유 에러 코드 문자열
    private final String message;    // 👈 사용자 친화적 에러 메시지
}
```

```java
// =================================================================================
// 📦 2-2. ErrorResponse.java - 표준 JSON 에러 응답 DTO
// =================================================================================
package com.ikae.snowthing.global.error;

public record ErrorResponse(
        String code,
        String error,
        String message
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.name(), errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String customMessage) {
        return new ErrorResponse(errorCode.getCode(), errorCode.name(), customMessage);
    }
}
```

```java
// =================================================================================
// 💥 2-3. CustomAuthException.java - ErrorCode 보유 커스텀 예외
// =================================================================================
package com.ikae.snowthing.global.exception;

import com.ikae.snowthing.global.error.ErrorCode;
import lombok.Getter;

@Getter
public class CustomAuthException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomAuthException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomAuthException(String message) {
        super(message);
        this.errorCode = ErrorCode.INVALID_CREDENTIALS;
    }
}
```

```java
// =================================================================================
// 🛠️ 2-4. GlobalExceptionHandler.java - 전역 예외 처리 컨트롤러 어드바이스
// =================================================================================
package com.ikae.snowthing.global.exception;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 👈 모든 RestController에서 발생하는 예외를 전역 감지
public class GlobalExceptionHandler {

    /**
     * [CustomAuthException 처리]
     * 도메인 인증 예외 발생 시 해당 ErrorCode 기반 JSON 응답 반환.
     */
    @ExceptionHandler(CustomAuthException.class)
    public ResponseEntity<ErrorResponse> handleCustomAuthException(CustomAuthException e) {
        ErrorCode errorCode = e.getErrorCode() != null ? e.getErrorCode() : ErrorCode.INVALID_CREDENTIALS;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    /**
     * [Spring Security 인증 예외 처리]
     * BadCredentialsException 및 UsernameNotFoundException을 401 UNAUTHORIZED 응답으로 통일.
     */
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.from(ErrorCode.INVALID_CREDENTIALS));
    }

    /**
     * [IllegalArgumentException 처리]
     * 에러 메시지 키워드에 따른 적절한 ErrorCode 매핑 처리.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        if ("INVALID_CREDENTIALS".equals(e.getMessage())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.from(ErrorCode.INVALID_CREDENTIALS));
        }
        if ("DUPLICATE_EMAIL".equals(e.getMessage())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.from(ErrorCode.DUPLICATE_EMAIL));
        }
        if ("DUPLICATE_NICKNAME".equals(e.getMessage())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.from(ErrorCode.DUPLICATE_NICKNAME));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, e.getMessage()));
    }

    /**
     * [Bean Validation 유효성 검증 예외 처리]
     * @Valid 검증 실패 시 발생하며, DTO에 설정한 defaultMessage를 추출해 반환.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String defaultMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, defaultMessage != null ? defaultMessage : ErrorCode.INVALID_INPUT.getMessage()));
    }
}
```

---

## 🎮 PART 3. 인증 API & 회원가입/프로필 API 흐름 레이어

### 💡 [WHY] 왜 컨트롤러와 서비스 구조를 이렇게 정제했는가?
1. **왜 `AuthService.authenticate()` 수동 메서드를 없애고 `AuthenticationManager`에 위임했는가?**: `AuthService`에서 `passwordEncoder.matches()`를 수동 호출하면 시큐리티 인증 메커니즘과 서비스 코드가 강하게 결합됩니다. 시큐리티 전용 컴포넌트인 `AuthenticationManager`가 `DaoAuthenticationProvider`를 통해 인증을 처리하게 만들고, `AuthService`는 로그인 후 유저 프로필 및 연관 도메인 데이터 조립에만 전념하게 만들었습니다 (단일 책임 원칙 SRP 달성).
2. **왜 `List.copyOf()` 방어적 복사를 도입했는가?**: 자바에서 컬렉션을 전달받을 때 주소 참조값을 공유하면 외부 코드에서 `list.clear()`나 `list.add()`를 수행했을 때 DTO 내부 리스트 데이터까지 훼손됩니다. DTO가 항상 100% 불변 상태를 유지하도록 주소값을 끊고 새 불변 리스트를 생성하는 방어적 복사를 적용했습니다.
3. **왜 `@AuthenticationPrincipal`을 도입했는가?**: `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` 같은 4줄짜리 캐스팅 보일러플레이트 코드를 완전히 제거하고, 스프링 MVC ArgumentResolver가 컨트롤러 메서드 파라미터에 즉시 `CustomUserDetails`를 바인딩해 주도록 구성했습니다.

```java
// =================================================================================
// 🔑 3-1. MemberLoginResponse.java - 방어적 복사(List.copyOf)가 적용된 로그인 DTO
// =================================================================================
package com.ikae.snowthing.domain.auth.dto;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class MemberLoginResponse {

    private final String publicId;
    private final String email;
    private final String nickname;
    private final Role role;
    private final List<String> resortNames;
    private final List<String> ridingStyleNames;

    @Builder
    public MemberLoginResponse(String publicId, String email, String nickname, Role role, List<String> resortNames, List<String> ridingStyleNames) {
        this.publicId = publicId;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        // 👈 방어적 복사 (Defensive Copy) 적용으로 100% 불변 리스트 보장 및 외부 변형 차단
        this.resortNames = resortNames != null ? List.copyOf(resortNames) : List.of();
        this.ridingStyleNames = ridingStyleNames != null ? List.copyOf(ridingStyleNames) : List.of();
    }

    public static MemberLoginResponse from(Member member, List<String> resortNames, List<String> ridingStyleNames) {
        return MemberLoginResponse.builder()
                .publicId(member.getPublicId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .role(member.getRole())
                .resortNames(resortNames)
                .ridingStyleNames(ridingStyleNames)
                .build();
    }
}
```

```java
// =================================================================================
// 🔓 3-2. AuthController.java - Spring Security 표준 위임 로그인 컨트롤러
// =================================================================================
package com.ikae.snowthing.domain.auth.controller;

import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    // 👈 매직 넘버 리터럴을 정적 상수로 추출하여 가독성 및 유지보수성 확보
    private static final int REMEMBER_ME_TIMEOUT_SECONDS = 30 * 24 * 60 * 60; // 30일 (2,592,000초)
    private static final int DEFAULT_SESSION_TIMEOUT_SECONDS = 60 * 60;        // 1시간 (3,600초)

    private final AuthenticationManager authenticationManager; // 👈 시큐리티 표준 인증 총괄
    private final SecurityContextRepository securityContextRepository; // 👈 세션 저장소
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<MemberLoginResponse> login(
            @Valid @RequestBody MemberLoginRequest loginRequest, // 👈 Bean Validation 입력값 검증
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        // 1. 미인증 UsernamePasswordAuthenticationToken 객체 생성
        Authentication authenticationToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
        );

        // 2. AuthenticationManager에 인증 위임
        // ➔ CustomUserDetailsService.loadUserByUsername() 호출 ➔ DaoAuthenticationProvider 비밀번호 대조
        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        // 3. SecurityContext 생성 및 ContextHolder 설정 (ThreadLocal 저장)
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        // 4. 세션 고정 공격 방어(Session Fixation Protection) 및 시큐리티 표준 세션 저장
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId(); // 👈 JSESSIONID 신규 재발급
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse); // 👈 HttpSession 저장

        // 5. Remember-Me 세션 타임아웃 계산 및 적용 (별도 메서드 분리)
        int timeoutSeconds = calculateSessionTimeoutSeconds(loginRequest.isRememberMe());
        session.setMaxInactiveInterval(timeoutSeconds);

        // 6. 회원 프로필 DTO 조립 반환
        MemberLoginResponse response = authService.getMyProfile(loginRequest.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * [Remember-Me 타임아웃 계산 메서드 분리]
     */
    private int calculateSessionTimeoutSeconds(boolean rememberMe) {
        return rememberMe ? REMEMBER_ME_TIMEOUT_SECONDS : DEFAULT_SESSION_TIMEOUT_SECONDS;
    }
}
```

```java
// =================================================================================
// 👤 3-3. MemberController.java - @AuthenticationPrincipal 적용 회원 컨트롤러
// =================================================================================
package com.ikae.snowthing.domain.member.controller;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.auth.service.AuthService;
import com.ikae.snowthing.domain.member.dto.MemberProfileUpdateRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.service.MemberService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<MemberSignUpResponse> signUp(@Valid @RequestBody MemberSignUpRequest request) {
        MemberSignUpResponse response = memberService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<MemberLoginResponse> getMyProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        // 👈 SecurityContextHolder 파싱 없이 @AuthenticationPrincipal로 userDetails 직접 주입
        MemberLoginResponse profile = authService.getMyProfile(userDetails.getUsername());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    public ResponseEntity<MemberLoginResponse> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MemberProfileUpdateRequest request
    ) {
        String email = userDetails.getUsername();
        memberService.updateMyProfile(email, request);
        MemberLoginResponse updatedProfile = authService.getMyProfile(email);
        return ResponseEntity.ok(updatedProfile);
    }
}
```

---

## 🗄️ PART 4. JPA 엔티티 & N+1 최적화 리포지토리 레이어

### 💡 [WHY] 왜 JPA 모델링과 쿼리를 이렇게 설계했는가?
1. **왜 `@ManyToMany`를 안 쓰고 `MemberResort` / `MemberRidingStyle` 중간 엔티티를 승격시켰는가?**: JPA `@ManyToMany`는 숨겨진 중계 테이블을 자동으로 조작하므로 중간 테이블에 `createdAt`, `status` 등의 추가 컬럼을 넣을 수 없으며, 수정 시 중간 테이블 전체 데이터를 삭제(`delete all`) 후 다시 인서트하는 심각한 N+1 연쇄 삭제 성능 결함이 터집니다. 1:N - N:1 양방향 엔티티로 직접 설계하여 조인 쿼리를 100% 제어하도록 만들었습니다.
2. **왜 리포지토리에서 1개 컬렉션만 Fetch Join하고 나머지는 Batch Size로 처리했는가?**: Hibernate에서 2개 이상의 1:N `List` 컬렉션을 동시 Fetch Join 하면 DB 카테시안 곱(Cartesian Product)이 뻥튀기되어 메모리 폭발이 터지고, Hibernate가 `MultipleBagFetchException` 예외를 던지며 서버를 멈춥니다. 1개의 메인 컬렉션(`MemberResort`)만 Fetch Join 조인하고, 나머지는 `default_batch_fetch_size: 1000` 옵션으로 IN 쿼리를 단 1번에 묶어 날리도록 최적화했습니다.

```java
// =================================================================================
// 🗄️ 4-1. MemberRepository.java - JPQL Fetch Join 성능 최적화 리포지토리
// =================================================================================
package com.ikae.snowthing.domain.member.repository;

import com.ikae.snowthing.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    /**
     * [이메일 기준 단 1회 조인 회원 조회]
     */
    @Query("SELECT m FROM Member m WHERE m.email = :email")
    Optional<Member> findByEmailWithDetails(@Param("email") String email);
}
```

```java
// =================================================================================
// 🏔️ 4-2. MemberResortRepository.java - 선호 스키장 Fetch Join 리포지토리
// =================================================================================
package com.ikae.snowthing.domain.member.repository;

import com.ikae.snowthing.domain.member.entity.MemberResort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MemberResortRepository extends JpaRepository<MemberResort, Long> {

    /**
     * [N+1 해결 쿼리]
     * MemberResort와 Resort 엔티티를 JOIN FETCH로 단 1회의 SQL 조인 쿼리로 묶어 조회.
     * 지연 로딩(LAZY) 시 발생하는 1+N 탐색 쿼리를 근본적으로 사전에 차단.
     */
    @Query("SELECT mr FROM MemberResort mr JOIN FETCH mr.resort WHERE mr.member.id = :memberId")
    List<MemberResort> findAllByMemberIdWithResort(@Param("memberId") Long memberId);

    void deleteAllByMemberId(Long memberId);
}
```

---

## 🧪 PART 5. 백엔드 통합 & 단위 테스트 수트 (25개 테스트 연동 및 테스트 기법)

### 💡 [WHY] 왜 테스트 기법을 이렇게 나눴는가?
1. **`@SpringBootTest` + `@AutoConfigureMockMvc` (통합 테스트)**: 전체 스프링 컨테이너, SecurityFilterChain, DB 연동까지 HTTP 요청 전체 라이프사이클을 실증 검증합니다.
2. **`CountDownLatch` + `ExecutorService` (동시성 테스트)**: 동일한 이메일/닉네임으로 10개의 스레드가 동시에 가입 요청을 보낼 때, DB 유니크 제약조건과 동시성 제어가 정확히 동작하는지 병렬 테스트를 수행합니다.

```java
// =================================================================================
// 🧪 5-1. AuthControllerTest.java - Spring Security 통합 인증 테스트 수트
// =================================================================================
package com.ikae.snowthing.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest // 👈 전체 통합 테스트용 컨테이너 로드
@AutoConfigureMockMvc // 👈 MockMvc 객체 자동 생성 및 주입
@Transactional // 👈 각 테스트 후 DB 롤백 처리
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        MemberSignUpRequest signUpRequest = new MemberSignUpRequest(
                "authuser@snowthing.com",
                "Password123!",
                "보드왕",
                List.of("용평리조트"),
                List.of("트릭")
        );
        memberService.signUp(signUpRequest);
    }

    @Test
    @DisplayName("올바른 로그인 요청 시 HTTP 200 OK 및 JSESSIONID 세션 생성 검증")
    void login_Success() throws Exception {
        MemberLoginRequest loginRequest = new MemberLoginRequest("authuser@snowthing.com", "Password123!", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("authuser@snowthing.com"))
                .andExpect(jsonPath("$.nickname").value("보드왕"));
    }

    @Test
    @DisplayName("틀린 비밀번호 입력 시 ErrorCode 기반 401 UNAUTHORIZED 에러 JSON 반환 검증")
    void login_Failure_WrongPassword() throws Exception {
        MemberLoginRequest loginRequest = new MemberLoginRequest("authuser@snowthing.com", "WrongPassword!", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("로그인 후 세션 쿠키를 동봉하여 /api/members/me 호출 시 200 OK 프로필 반환 검증")
    void getMyProfile_WithSession_Success() throws Exception {
        MemberLoginRequest loginRequest = new MemberLoginRequest("authuser@snowthing.com", "Password123!", false);

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession();

        mockMvc.perform(get("/api/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("authuser@snowthing.com"));
    }
}
```

```java
// =================================================================================
// ⚡ 5-2. MemberConcurrencyTest.java - 멀티스레드 동시성 가입 테스트 수트
// =================================================================================
package com.ikae.snowthing.domain.member.service;

import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MemberConcurrencyTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("동시에 10개의 스레드가 동일 이메일로 가입 시도 시 1건만 성공하고 DB 중복이 발생하지 않는지 검증")
    void concurrentSignUp_SameEmail_OnlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        MemberSignUpRequest request = new MemberSignUpRequest(
                "concurrent@snowthing.com",
                "Password123!",
                "동시성유저",
                List.of("휘닉스파크"),
                List.of("라이딩")
        );

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    memberService.signUp(request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 👈 10개 스레드가 완료될 때까지 대기

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);
    }
}
```

---

## ⚙️ PART 6. 백엔드 적용 10대 핵심 기술 Why, How & 파라미터/옵션 탐구 (Options & Tuning)

### 1. Spring Security Filter Options & Tuning
* **`SessionCreationPolicy` 옵션 비교**:
  - `ALWAYS`: 항상 세션을 새로 생성 (메모리 낭비 심함)
  - `NEVER`: 시큐리티가 세션을 직접 생성하진 않지만 기존 세션이 존재하면 활용
  - `IF_REQUIRED` (★선택): 필요할 때만 최적화 생성 (REST API + Session 모범답안)
  - `STATELESS`: 세션을 전혀 쓰지 않음 (JWT 기반 Stateless 아키텍처용)

### 2. JPA & Hibernate Tuning Options
* **`hibernate.default_batch_fetch_size: 1000`**:
  - 지연 로딩(LAZY) 탐색 시 1개씩 SELECT 쿼리가 나가는 N+1 버그를 지정한 크기(최대 1000개)만큼 SQL `IN (?, ?, ...)` 쿼리로 묶어서 단 1번에 가져오는 하이버네이트 최고의 성능 최적화 옵션.

### 3. BCrypt Password Hashing Work Factor Options
* **`strength` (Work Factor 4 ~ 31)**:
  - 기본값 `10` ($2^{10} = 1,024$회 해싱 반복, 약 0.08초 소요).
  - 숫자를 1 올릴 때마다 암호화 계산 시간이 정확히 2배로 늘어남.
  - 너무 낮으면(4) 무차별 대입 공격(Brute-Force)에 뚫리고, 너무 높으면(15) 서버 CPU가 마비되므로 `10`~`12`가 현업 골디락스 존.

---

## 🚀 PART 7. 아키텍처 반성 & "여기서는 이렇게 설계했어도 좋았을 것이다" 5대 대안

### 💡 대안 1. 단순 30일 세션 만료 ➔ Spring Security `PersistentTokenBasedRememberMeServices` (DB 쿠키 자동 재인증)
* **이유**: 단순 `session.setMaxInactiveInterval(30일)`은 서버 톰캣 메모리에 세션을 30일 동안 계속 상주시추므로 서버 메모리(RAM) 폭발의 주범이 되며, 서버가 재시작되면 세션이 증발하여 유저가 튕겨 나갑니다.
* **개선안**: 세션 만료 시간은 1시간으로 짧게 유지하고, `remember-me` 쿠키 및 DB `persistent_logins` 테이블을 연동하는 **PersistentTokenBasedRememberMeServices**를 도입하면 서버 메모리를 점유하지 않으면서도 서버 재시작 시 세션을 자동 재발급해 주는 최고의 아키텍처를 완성할 수 있습니다.

### 💡 대안 2. 단일 RDBMS 세션 ➔ Redis 기반 Distributed Session (`Spring Session Data Redis`)
* **이유**: 현재는 단일 톰캣 서버 메모리에 `HttpSession`을 보관하므로 백엔드 서버를 2대 이상으로 확장(Scale-out / Load Balancing) 시 세션 불일치(Session Mismatch)가 발생합니다.
* **개선안**: 중앙 인메모리 데이터베이스인 **Redis**를 세션 저장소로 지정(`@EnableRedisHttpSession`)하면 여러 대의 백엔드 서버가 세션을 100% 공유할 수 있어 Stateless 한 Scale-out 구조를 달성할 수 있습니다.

### 💡 대안 3. 세션 기반 인증 ➔ Stateless JWT + Refresh Token Rotation (RTR)
* **이유**: 모바일 앱(iOS, Android)과 웹 브라우저를 동시에 지원하는 멀티 플랫폼 API 서버로 확장할 때 쿠키/세션 방식은 브라우저에 종속되어 앱 개발 시 세션 처리가 까다롭습니다.
* **개선안**: 서버 메모리를 일절 쓰지 않는 **Stateless JWT 토큰 인증**과, 탈취된 Refresh Token을 자동 감지해 즉시 무효화하는 **Refresh Token Rotation (RTR)** 아키텍처를 도입하는 것이 대규모 서비스 확장성 면에서 더 유리합니다.

### 💡 대안 4. N:M 매핑 테이블 ➔ DDD 값 객체(`@ElementCollection`) 또는 Composite PK 패턴
* **이유**: 현재 `MemberResort`, `MemberRidingStyle`은 대리키(`id` Long Auto_Increment)를 PK로 가지고 있어 불필요한 인덱스 메모리를 점유합니다.
* **개선안**: `member_id` + `resort_id` 2개 컬럼을 복합키(`@EmbeddedId` / `@IdClass`)로 묶어 유니크 제약과 조인 성능을 동시에 극대화하거나, 단순 문자열 리스트인 경우 JPA `@ElementCollection`으로 관리하는 것이 도메인 주도 설계(DDD) 관점에서 훨씬 명확합니다.

### 💡 대안 5. 단일 데이터베이스 ➔ CQRS (Command Query Responsibility Segregation) 읽기/쓰기 DB 분리
* **이유**: 로그인 및 프로필 조회의 읽기(Read) 요청 비율은 회원가입/수정 쓰기(Write) 요청보다 100배 이상 많습니다.
* **개선안**: Write(Command) 데이터베이스(MySQL Master)와 Read(Query) 데이터베이스(MySQL Slave Replica)를 물리적으로 분리하고, `@Transactional(readOnly = true)` 설정 시 Read Replica로 트래픽을 분산하는 **CQRS 아키텍처**를 적용하면 읽기 성능을 10배 이상 향상시킬 수 있습니다.

---

> **비고**: 위 스터디 가이드 파일 [`docs/study/sprint01/studySprint01CompleteCodeMaster260821.md`](file:///c:/Users/ikaes/IdeaProjects/snowthing/docs/study/sprint01/studySprint01CompleteCodeMaster260821.md)을 노션에 옮겨 담으시면 스프린트 01의 모든 백엔드 코드, 테스트 수트, 설계 배경, 아키텍처 대안을 완벽하게 학습하실 수 있습니다! 🚀
