package com.coresolution.pe.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.service.CustomUserDetailsService;
import com.coresolution.pe.service.PeService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
public class LoginCotroller {
    @Autowired
    AuthenticationManager authManager;
    @Autowired
    private PeService pe;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CustomUserDetailsService userDetailsService;
    @Autowired
    private SecurityContextRepository securityContextRepository;

    @PostMapping("/pwdAction/{idx}")
    public ResponseEntity<Map<String, Object>> pwdAction(
            @PathVariable String idx,
            @RequestParam String pwd,
            @RequestParam String pwd2,
            HttpServletRequest request,
            HttpServletResponse response) {

        Map<String, Object> res = new HashMap<>();

        // 1) 입력값 검증
        if (!pwd.equals(pwd2)) {
            res.put("result", "비밀번호가 일치하지 않습니다.");
            return ResponseEntity.ok(res);
        }

        // 2) 사용자 조회
        UserPE user = pe.findUserInfoByIdx(Integer.parseInt(idx));
        if (user == null) {
            res.put("result", "사용자를 찾을 수 없습니다.");
            return ResponseEntity.ok(res);
        }

        // 3) 이미 설정된 경우
        if (user.getPwd() != null && !user.getPwd().isEmpty()) {
            res.put("result", "이미 비밀번호가 설정되어 있습니다.");
            return ResponseEntity.ok(res);
        }

        // 4) 비밀번호 설정
        String encoded = passwordEncoder.encode(pwd);
        user.setPwd(encoded);
        int updated = pe.updateUserPassword(user);
        if (updated == 0) {
            res.put("result", "업데이트 대상이 없거나 이미 설정되었습니다.");
            return ResponseEntity.ok(res);
        }

        // 5) 자동 로그인: UserDetails 로드 → 3인자 authenticated 토큰 생성 → 세션 저장
        UserDetails ud = userDetailsService.loadUserByUsername(user.getId());
        var auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        request.getSession(true);
        securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);

        // 6) 성공 응답
        res.put("redirectUrl", "/Info");
        return ResponseEntity.ok(res);
    }
}