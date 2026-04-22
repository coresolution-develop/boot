package com.coresolution.pe.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

import com.coresolution.pe.handler.AffAuthenticationSuccessHandler;
import com.coresolution.pe.handler.AffInstAdminSuccessHandler;
import com.coresolution.pe.handler.CustomAccessDeniedHandler;
import com.coresolution.pe.handler.CustomAuthenticationSuccessHandler;
import com.coresolution.pe.handler.InstAdminSuccessHandler;
import com.coresolution.pe.security.CustomSecurityContextRepository;
import com.coresolution.pe.security.filter.AffInstAdminAuthenticationFilter;
import com.coresolution.pe.security.filter.CustomAuthenticationFilter;
import com.coresolution.pe.security.filter.InstAdminAuthenticationFilter;
import com.coresolution.pe.security.provider.AffAuthenticationProvider;
import com.coresolution.pe.security.provider.CustomAuthenticationProvider;
import com.coresolution.pe.security.provider.InstAdminAuthenticationProvider;
import com.coresolution.pe.service.PeService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

        // 1) 비밀번호 암호화 빈
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        // 2) AuthenticationManager 는 AuthenticationConfiguration 에 위임
        @Bean
        public AuthenticationManager authenticationManager(
                        AuthenticationConfiguration authConfig) throws Exception {
                return authConfig.getAuthenticationManager();
        }

        // 3) 로그인 실패 핸들러 (PeService 주입)
        @Bean("peFailureHandler")
        public AuthenticationFailureHandler failureHandler(PeService peService) {
                return (request, response, exception) -> {
                        String result = "1"; // 기본값: 비밀번호/이름 불일치
                        String idx = "";
                        String id = request.getParameter("id"); // 요청 파라미터에서 ID 가져오기
                        String loginType = request.getParameter("loginType"); // byName | byPwd

                        if (exception instanceof LockedException le) {
                                // 🔴 LockedException 상세 분기
                                String msg = le.getMessage();
                                if ("PWD_NOT_SET".equals(msg)) {
                                        // 비밀번호 미설정
                                        result = "5";
                                        if (id != null && !id.isEmpty()) {
                                                Integer found = peService.findIdx(id);
                                                idx = (found != null) ? found.toString() : "";
                                        }
                                } else if ("NOT_TARGET".equals(msg)) {
                                        // 평가 대상자가 아님 (del_yn = 'Y')
                                        result = "0";
                                        // idx는 프론트에서 안 쓰니 굳이 안 채워도 됨
                                } else {
                                        // 혹시 다른 LockedException 이면 예비 코드
                                        result = "99";
                                }

                        } else if (exception instanceof CredentialsExpiredException) {
                                result = "2"; // 비밀번호로 로그인 필요 (또는 만료)
                                if (id != null && !id.isEmpty()) {
                                        Integer found = peService.findIdx(id);
                                        idx = (found != null) ? found.toString() : "";
                                }

                        } else if (exception instanceof AuthenticationServiceException) {
                                // CustomAuthenticationProvider에서 던지는 나머지 서비스 예외
                                // (Unknown loginType, 예상치 못한 결과 등)
                                String errorMessage = exception.getMessage();
                                if ("2025년도 직원근무평가 대상직원이 아닙니다.".equals(errorMessage)) {
                                        // 만약 이 경로로도 던지는 코드가 남아 있다면 방어용
                                        result = "0";
                                } else {
                                        result = "99"; // 기타 서비스 예외
                                }

                        } else if (exception instanceof BadCredentialsException) {
                                // "이름이 일치하지 않습니다." 또는 "비밀번호가 일치하지 않습니다."
                                String errorMessage = exception.getMessage();
                                if ("이름이 일치하지 않습니다.".equals(errorMessage)) {
                                        result = "3"; // 이름 불일치
                                } else {
                                        result = "1"; // 비밀번호 불일치 (기본)
                                }

                        } else if (exception instanceof UsernameNotFoundException) {
                                // 사번 자체가 DB에 없는 경우
                                // (기존에 0으로 쓰고 있었다면 유지, 아니면 다른 코드로 빼도 됨)
                                result = "0";
                        }

                        System.out.println("AuthenticationFailureHandler - 로그인 실패. Result: " + result
                                        + ", Exception: " + exception.getMessage());

                        // ★★★ 실패해도 '방금 입력한 값'을 쿠키에 저장 ★★★
                        final String ctx = request.getContextPath();
                        final String cookiePath = (ctx == null || ctx.isEmpty()) ? "/" : ctx;
                        final int maxAge = 60 * 60 * 24 * 30;

                        if (id != null && !id.isEmpty()) {
                                Cookie idCookie = new Cookie("savedId",
                                                URLEncoder.encode(id, StandardCharsets.UTF_8));
                                idCookie.setPath(cookiePath);
                                idCookie.setMaxAge(maxAge);
                                response.addCookie(idCookie);
                        }

                        Cookie typeCookie = new Cookie("savedLoginType",
                                        (loginType == null || loginType.isBlank()) ? "byName" : loginType);
                        typeCookie.setPath(cookiePath);
                        typeCookie.setMaxAge(maxAge);
                        response.addCookie(typeCookie);

                        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
                        if (isAjax) {
                                // ✅ AJAX: 401 + JSON
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write(
                                                "{\"ok\":false,\"result\":\"" + result + "\",\"idx\":\"" + idx + "\"}");
                                return;
                        }

                        // ✅ 일반 폼 제출: 에러 코드로 리다이렉트
                        String redirect = "login?error=" + result + (idx.isEmpty() ? "" : "&idx=" + idx);
                        response.sendRedirect(redirect);
                };
        }

        // AFF 전용 로그인 실패 핸들러 (리다이렉트: /aff/login)
        // PeAffService 이름은 현재 프로젝트의 실제 서비스명으로 맞춰주세요.
        @Bean("affFailureHandler")
        public AuthenticationFailureHandler affFailureHandler(com.coresolution.pe.service.PeAffService peAffService) {
                return (request, response, exception) -> {
                        String result = "1";
                        String idx = "";
                        String id = request.getParameter("id");
                        String loginType = request.getParameter("loginType");

                        if (exception instanceof LockedException) {
                                result = "5";
                                if (id != null && !id.isEmpty()) {
                                        idx = peAffService.findIdx(id) != null ? peAffService.findIdx(id).toString()
                                                        : "";
                                }
                        } else if (exception instanceof CredentialsExpiredException) {
                                result = "2";
                                if (id != null && !id.isEmpty()) {
                                        idx = peAffService.findIdx(id) != null ? peAffService.findIdx(id).toString()
                                                        : "";
                                }
                        } else if (exception instanceof AuthenticationServiceException) {
                                String errorMessage = exception.getMessage();
                                result = "2025년도 직원근무평가 대상직원이 아닙니다.".equals(errorMessage) ? "0" : "99";
                        } else if (exception instanceof BadCredentialsException) {
                                String errorMessage = exception.getMessage();
                                result = "이름이 일치하지 않습니다.".equals(errorMessage) ? "3" : "1";
                        } else if (exception instanceof UsernameNotFoundException) {
                                result = "0";
                        }

                        final String ctx = request.getContextPath();
                        final String cookiePath = (ctx == null || ctx.isEmpty()) ? "/" : ctx;
                        final int maxAge = 60 * 60 * 24 * 30;

                        if (id != null && !id.isEmpty()) {
                                Cookie idCookie = new Cookie("savedId", URLEncoder.encode(id, StandardCharsets.UTF_8));
                                idCookie.setPath(cookiePath);
                                idCookie.setMaxAge(maxAge);
                                response.addCookie(idCookie);
                        }
                        Cookie typeCookie = new Cookie("savedLoginType",
                                        (loginType == null || loginType.isBlank()) ? "byName" : loginType);
                        typeCookie.setPath(cookiePath);
                        typeCookie.setMaxAge(maxAge);
                        response.addCookie(typeCookie);

                        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
                        if (isAjax) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write(
                                                "{\"ok\":false,\"result\":\"" + result + "\",\"idx\":\"" + idx + "\"}");
                                return;
                        }

                        // 🔁 aff 영역
                        String redirect = "/aff/login?error=" + result + (idx.isEmpty() ? "" : "&idx=" + idx);
                        response.sendRedirect(redirect);
                };
        }

        // ─────────────────────────────────────────────────────
        // [Order 1] 기관 관리자 전용 FilterChain (/pe/inst-admin/**)
        // ─────────────────────────────────────────────────────
        @Bean
        @Order(1)
        SecurityFilterChain instAdminChain(
                        HttpSecurity http,
                        InstAdminAuthenticationProvider instAdminProvider,
                        InstAdminSuccessHandler instAdminSuccessHandler,
                        CustomSecurityContextRepository repo) throws Exception {

                http.securityMatcher(
                                "/pe/inst-admin/**",
                                "/pe/inst-login",
                                "/pe/inst-login/**",
                                "/pe/inst-loginAction")
                                .csrf(AbstractHttpConfigurer::disable)
                                .authenticationProvider(instAdminProvider)
                                .securityContext(c -> c.securityContextRepository(repo))
                                .requestCache(cache -> cache.disable())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(
                                                                new LoginUrlAuthenticationEntryPoint("/pe/inst-login")))
                                .authorizeHttpRequests(a -> a
                                                .requestMatchers(
                                                                "/pe/inst-login", "/pe/inst-loginAction",
                                                                "/css/**", "/js/**", "/img/**", "/fonts/**",
                                                                "/icon/**", "/favicon/**", "/error")
                                                .permitAll()
                                                .requestMatchers("/pe/inst-admin/**").hasRole("INST_ADMIN")
                                                .anyRequest().authenticated())
                                .logout(l -> l
                                                .logoutUrl("/pe/inst-admin/logout")
                                                .logoutSuccessUrl("/pe/inst-login")
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true))
                                .formLogin(AbstractHttpConfigurer::disable);

                var instManager = new org.springframework.security.authentication.ProviderManager(
                                java.util.List.of(instAdminProvider));

                // 실패 핸들러: 단순 리다이렉트
                AuthenticationFailureHandler instFailureHandler = (req, res, ex) -> {
                        String msg = ex.getMessage() != null ? ex.getMessage() : "로그인 실패";
                        res.sendRedirect("/pe/inst-login?error=1");
                };

                var instFilter = new InstAdminAuthenticationFilter(instManager);
                instFilter.setAuthenticationSuccessHandler(instAdminSuccessHandler);
                instFilter.setAuthenticationFailureHandler(instFailureHandler);

                http.addFilterAt(instFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        // ─────────────────────────────────────────────────────
        // [Order 2] 계열사 기관 관리자 전용 FilterChain (/aff/inst-admin/**)
        // ─────────────────────────────────────────────────────
        @Bean
        @Order(2)
        SecurityFilterChain affInstAdminChain(
                        HttpSecurity http,
                        InstAdminAuthenticationProvider instAdminProvider,
                        AffInstAdminSuccessHandler affInstAdminSuccessHandler,
                        CustomSecurityContextRepository repo) throws Exception {

                http.securityMatcher(
                                "/aff/inst-admin/**",
                                "/aff/inst-login",
                                "/aff/inst-login/**",
                                "/aff/inst-loginAction")
                                .csrf(AbstractHttpConfigurer::disable)
                                .authenticationProvider(instAdminProvider)
                                .securityContext(c -> c.securityContextRepository(repo))
                                .requestCache(cache -> cache.disable())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(
                                                                new LoginUrlAuthenticationEntryPoint("/aff/inst-login")))
                                .authorizeHttpRequests(a -> a
                                                .requestMatchers(
                                                                "/aff/inst-login", "/aff/inst-loginAction",
                                                                "/css/**", "/js/**", "/img/**", "/fonts/**",
                                                                "/icon/**", "/favicon/**", "/error")
                                                .permitAll()
                                                .requestMatchers("/aff/inst-admin/**").hasRole("INST_ADMIN")
                                                .anyRequest().authenticated())
                                .logout(l -> l
                                                .logoutUrl("/aff/inst-admin/logout")
                                                .logoutSuccessUrl("/aff/inst-login")
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true))
                                .formLogin(AbstractHttpConfigurer::disable);

                var instManager = new org.springframework.security.authentication.ProviderManager(
                                java.util.List.of(instAdminProvider));

                AuthenticationFailureHandler affInstFailureHandler = (req, res, ex) ->
                        res.sendRedirect("/aff/inst-login?error=1");

                var affInstFilter = new AffInstAdminAuthenticationFilter(instManager);
                affInstFilter.setAuthenticationSuccessHandler(affInstAdminSuccessHandler);
                affInstFilter.setAuthenticationFailureHandler(affInstFailureHandler);

                http.addFilterAt(affInstFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        // ─────────────────────────────────────────────────────
        // [Order 3] AFF 체인
        // ─────────────────────────────────────────────────────
        @Bean
        @Order(3)
        SecurityFilterChain affChain(
                        HttpSecurity http,
                        AffAuthenticationProvider affProvider,
                        AffAuthenticationSuccessHandler affSuccessHandler,
                        @Qualifier("affFailureHandler") AuthenticationFailureHandler affFailureHandler,
                        CustomSecurityContextRepository repo) throws Exception {

                http.securityMatcher(new AndRequestMatcher(
                        new AntPathRequestMatcher("/aff/**"),
                        new NegatedRequestMatcher(new AntPathRequestMatcher("/aff/inst-admin/**")),
                        new NegatedRequestMatcher(new AntPathRequestMatcher("/aff/inst-login*")),
                        new NegatedRequestMatcher(new AntPathRequestMatcher("/aff/inst-loginAction*"))))
                                .csrf(AbstractHttpConfigurer::disable)
                                .authenticationProvider(affProvider)
                                .securityContext(c -> c.securityContextRepository(repo))
                                .requestCache(cache -> cache.disable())
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint(
                                                                new LoginUrlAuthenticationEntryPoint("/aff/login")))
                                .authorizeHttpRequests(a -> a
                                                .requestMatchers(
                                                                "/aff/login", "/aff/loginAction",
                                                                "/aff/pwdfind", "/aff/pwd/**", "/aff/pwdAction/**",
                                                                "/css/**", "/js/**", "/img/**", "/fonts/**", "/icon/**",
                                                                "/favicon/**",
                                                                "/error", "/ping")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .logout(l -> l.logoutUrl("/aff/logout").logoutSuccessUrl("/aff/login"))
                                .formLogin(AbstractHttpConfigurer::disable);

                var affManager = new org.springframework.security.authentication.ProviderManager(
                                java.util.List.of(affProvider));

                var affFilter = new com.coresolution.pe.security.filter.CustomAuthenticationFilter(affManager);
                affFilter.setFilterProcessesUrl("/aff/loginAction");
                affFilter.setAuthenticationSuccessHandler(affSuccessHandler);
                affFilter.setAuthenticationFailureHandler(affFailureHandler);

                http.addFilterAt(affFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        // ─────────────────────────────────────────────────────
        // [Order 4] PE 메인 체인 (직원 로그인 + 어드민)
        // ─────────────────────────────────────────────────────
        @Bean
        @Order(4)
        public SecurityFilterChain filterChain(
                        HttpSecurity http,
                        CustomAuthenticationProvider customAuthProvider,
                        CustomAuthenticationSuccessHandler successHandler,
                        CustomAccessDeniedHandler accessDeniedHandler,
                        @Qualifier("peFailureHandler") AuthenticationFailureHandler failureHandler,
                        CustomSecurityContextRepository repo) throws Exception {

                // /aff/** 와 /pe/inst-admin/**, /pe/inst-login* 을 모두 제외
                http.securityMatcher(new AndRequestMatcher(
                        new NegatedRequestMatcher(new AntPathRequestMatcher("/aff/**")),
                        new NegatedRequestMatcher(new AntPathRequestMatcher("/pe/inst-admin/**")),
                        new NegatedRequestMatcher(new AntPathRequestMatcher("/pe/inst-login*")),
                        new NegatedRequestMatcher(new AntPathRequestMatcher("/pe/inst-loginAction*"))))
                                .csrf(AbstractHttpConfigurer::disable)
                                .authenticationProvider(customAuthProvider)
                                .requestCache(cache -> cache.disable())
                                .securityContext(ctx -> ctx.securityContextRepository(repo))
                                .exceptionHandling(ex -> ex
                                                .accessDeniedHandler(accessDeniedHandler)
                                                .authenticationEntryPoint(
                                                                new LoginUrlAuthenticationEntryPoint("/login")))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/css/**", "/js/**", "/img/**", "/fonts/**", "/icon/**",
                                                                "/favicon/**",
                                                                "/login", "/loginAction", "/pwdfind", "/pwd/**",
                                                                "/pwdAction/**",
                                                                "/error", "/ping")
                                                .permitAll()

                                                // 예외/세부 룰은 /admin/** 보다 먼저
                                                .requestMatchers("/admin/notices/**").permitAll()
                                                .requestMatchers("/admin/notice").authenticated()
                                                .requestMatchers("/admin/**").hasRole("ADMIN")

                                                .requestMatchers("/Info/**").hasAnyRole("USER", "ADMIN")
                                                .anyRequest().authenticated())
                                .logout(logout -> logout
                                                .logoutUrl("/Logout")
                                                .logoutSuccessUrl("/login")
                                                .permitAll())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                                .maximumSessions(1)
                                                .maxSessionsPreventsLogin(false))
                                .formLogin(AbstractHttpConfigurer::disable);

                var peManager = new org.springframework.security.authentication.ProviderManager(
                                java.util.List.of(customAuthProvider));

                var caf = new com.coresolution.pe.security.filter.CustomAuthenticationFilter(peManager);
                caf.setFilterProcessesUrl("/loginAction");
                caf.setAuthenticationSuccessHandler(successHandler);
                caf.setAuthenticationFailureHandler(failureHandler);

                http.addFilterAt(caf, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

}
