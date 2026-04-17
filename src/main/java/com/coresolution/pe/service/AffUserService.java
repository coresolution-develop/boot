package com.coresolution.pe.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffLoginMapper;

@Service
public class AffUserService {
    private final AffLoginMapper loginMapper;

    @Value("${app.current.eval-year}")
    private int currentEvalYear;

    public AffUserService(AffLoginMapper loginMapper) {
        this.loginMapper = loginMapper;
    }

    public int login(String id, String credential, String loginType) {
        System.out.println("PeService.login: id=" + id + ", credential=" + credential
                + ", loginType=" + loginType);
        if (!"byName".equals(loginType)) {
            throw new IllegalArgumentException("byPwd는 CustomAuthenticationProvider에서 처리합니다.");
        }
        int cnt = loginMapper.countByIdAndName(id, credential, currentEvalYear);
        if (cnt == 0)
            return 3;
        int pwdCnt = loginMapper.countPwdById(id, currentEvalYear);
        return pwdCnt == 0 ? 5 : 2;
    }

    public UserPE findUserInfoByIdx(int idx) {
        return loginMapper.findUserInfoByIdx(idx, currentEvalYear);
    }

    public UserPE findUserInfoById(String id, int year) {
        UserPE user = loginMapper.findById(id, year);
        if (user == null) {
            System.out.println("사용자를 찾을 수 없습니다: " + id);
            return null;
        }
        System.out.println("사용자 정보 조회: " + user);
        return user;
    }

    /** 로그인 성공 시 사용할 idx 조회 */
    public Integer findIdx(String id) {
        return loginMapper.findIdxById(id, currentEvalYear);
    }


}
