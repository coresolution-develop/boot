package com.coresolution.pe.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.method.P;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.service.PeAffService;
import com.coresolution.pe.service.PeService;

@RestController
public class AffLoginCotroller {
    @Autowired
    AuthenticationManager authManager;
    @Autowired
    private PeAffService pe;
    @Autowired
    private PasswordEncoder passwordEncoder; // 추가

    @PostMapping("/aff/pwdAction/{idx}")
    public ResponseEntity<Map<String, Object>> pwdAction(
            @PathVariable String idx,
            @RequestParam String pwd,
            @RequestParam String pwd2) {

        Map<String, Object> response = new HashMap<>();

        // 1) 입력값 검증
        if (!pwd.equals(pwd2)) {
            response.put("result", "비밀번호가 일치하지 않습니다.");
            return ResponseEntity.ok(response);
        }

        // 2) 사용자 조회
        UserPE user = pe.findUserInfoByIdx(Integer.parseInt(idx));
        if (user == null) {
            response.put("result", "사용자를 찾을 수 없습니다.");
            return ResponseEntity.ok(response);
        }

        // 3) 이미 설정된 경우
        if (user.getPwd() != null && !user.getPwd().isEmpty()) {
            response.put("result", "이미 비밀번호가 설정되어 있습니다.");
            return ResponseEntity.ok(response);
        }

        // 4) 비밀번호 설정
        String encoded = passwordEncoder.encode(pwd);
        user.setPwd(encoded);
        int updated = pe.updateUserPassword(user);
        if (updated == 0) {
            response.put("result", "업데이트 대상이 없거나 이미 설정되었습니다.");
            return ResponseEntity.ok(response);
        }
        // (선택) 바로 로그인 컨텍스트에 등록
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId(), encoded));

        // 5) 성공 응답
        response.put("redirectUrl", "/aff/login");
        return ResponseEntity.ok(response);
    }
}