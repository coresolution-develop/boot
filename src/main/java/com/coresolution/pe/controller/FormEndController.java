package com.coresolution.pe.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.coresolution.pe.entity.EndLetter;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.EndLetterMapper;
import com.coresolution.pe.service.PeService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class FormEndController {

    @Autowired
    private PeService pe;
    private final EndLetterMapper endLetterMapper;
    @Value("${app.exhibition.mask-personal:false}")
    private boolean exhibitionMaskPersonal;

    /**
     * 평가 완료 페이지
     * - JS에서 이동하던 경로와 맞춤: /pe/FormEnd/{eval}/{target}
     */
    @GetMapping("/FormEnd/{eval}/{target}")
    public String formEnd(@PathVariable("eval") String pathEvalId,
            @PathVariable("target") String targetId,
            @RequestParam(value = "year", defaultValue = "${app.current.eval-year}") String year,
            Authentication auth,
            Model model) {

        // 로그인 확인
        if (auth == null || auth.getName() == null) {
            // 스프링 시큐리티 설정에 따라 로그인 페이지 매핑 확인
            return "redirect:/pe/login";
        }
        String authId = auth.getName();

        // URL의 eval과 실제 로그인 사용자가 다른 경우 방어 (필요 정책에 맞춰 처리)
        if (!authId.equals(pathEvalId)) {
            // 1) 그냥 로그인 사용자 기준으로 덮어쓰기:
            pathEvalId = authId;
            // 2) 또는 차단:
            // throw new AccessDeniedException("잘못된 접근입니다.");
        }

        // 헤더/레이아웃에서 쓰는 사용자 정보
        UserPE userInfo = pe.findByUserIdWithNames(pathEvalId, year);
        if (userInfo == null)
            throw new AccessDeniedException("사용자 정보를 찾을 수 없습니다.");

        // 대상자 정보
        UserPE target = pe.findByUserIdWithNames(targetId, year);
        if (target == null)
            throw new AccessDeniedException("대상자 정보를 찾을 수 없습니다.");

        // 완료 편지 (없으면 null → 템플릿에서 기본 문구 표시)
        EndLetter letter = null;
        try {
            letter = endLetterMapper.findByYearAndInstitution(
                    Integer.parseInt(year), userInfo.getCName());
        } catch (Exception ignored) { /* 테이블 미존재 등 예외 무시 */ }

        // 모델 바인딩
        model.addAttribute("userInfo", userInfo);
        model.addAttribute("target", target);
        model.addAttribute("year", year);
        model.addAttribute("evaluatorId", pathEvalId);
        model.addAttribute("maskPersonalInfo", exhibitionMaskPersonal);
        model.addAttribute("letter", letter);

        return "pe/user/formend"; // 위에서 만든 템플릿 경로
    }
}
