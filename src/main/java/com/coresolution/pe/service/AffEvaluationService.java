package com.coresolution.pe.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.coresolution.pe.controller.PageController;
import com.coresolution.pe.entity.AdminDefaultTarget;
import com.coresolution.pe.entity.AffAdminDefaultTarget;
import com.coresolution.pe.entity.EvalMeta;
import com.coresolution.pe.entity.SplitTargets;
import com.coresolution.pe.entity.UserPE;
import com.coresolution.pe.mapper.AffDefaultTargetMapper;
import com.coresolution.pe.mapper.AffLoginMapper;
import com.coresolution.pe.mapper.AffUserMapper;
import com.coresolution.pe.mapper.AffUserRolesMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AffEvaluationService {
    private final AffUserMapper userMapper; // personnel_evaluation_aff.users_
    private final AffUserRolesMapper rolesMapper; // personnel_evaluation_aff.user_roles_
    private final AffDefaultTargetMapper defMap; // personnel_evaluation_aff.admin_default_targets
    private final AffLoginMapper loginMapper; // 전체 users_ 조회 (AFF)

    private static final Logger log = LoggerFactory.getLogger(AffEvaluationService.class);

    // 병원 그룹(c_name 기준)
    private static final Set<String> HOSPITAL_ORGS = Set.of(
            "효사랑전주요양병원",
            "효사랑가족요양병원",
            "가족사랑요양병원");

    // 병원 + 사랑모아/정성모아/자야클린 여부
    private static boolean isHospitalLike(String org, String group) {
        String o = nz(org);
        String g = nz(group);

        boolean hospitalOrg = HOSPITAL_ORGS.contains(o);
        boolean smJsJyGroup = g.contains("사랑모아") ||
                g.contains("정성모아") ||
                g.contains("자야클린");

        return hospitalOrg || smJsJyGroup;
    }

    private enum Scope {
        ORG, AGC, SUB, NONE
    }

    // ====== PUBLIC API ======

    /** 전 직원 루프 → 기본 대상 재작성 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int rebuildAdminDefaultTargets(int year) {
        List<UserPE> all = loginMapper.getUserList(year);
        if (all == null || all.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (UserPE u : all) {
            total += rebuildForOne(u.getId(), year);
        }
        log.info("[AFF][INIT] year={}, upserts={}", year, total);
        return total;
    }

    /** 한 명에 대한 기본대상 재계산/업서트 (AFF 전용) */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public int rebuildForOne(String evaluatorId, int year) {
        // 1) 기존 삭제
        defMap.deleteByUserAndYear(evaluatorId, year);

        // 2) 기본 정보 + 캐시 준비
        UserPE me = userMapper.findById(evaluatorId, year);
        if (me == null) {
            return 0;
        }

        RoleCache roleCache = new RoleCache(rolesMapper, year);
        SubCodeCache subCodeCache = new SubCodeCache(userMapper, year);

        // 3) 규칙에 맞는 후보(targets) 수집
        List<UserPE> targets = collectTargetsByRule(me, year, roleCache, subCodeCache);
        if (targets == null || targets.isEmpty()) {
            log.info("[AFF][DEBUG] evaluator={} cName='{}', cName2='{}', subName='{}', roles={}",
                    evaluatorId, nz(me.getCName()), nz(me.getCName2()),
                    ensureSubName(me, subCodeCache),
                    roleCache.rolesOf(evaluatorId));
            return 0;
        }

        // 4) 메타 생성 → UPSERT
        int cnt = 0;
        for (UserPE t : targets) {
            EvalMeta m = buildMeta(me, t, year, roleCache, subCodeCache);
            if (m == null) {
                continue; // 폼 매핑 불가 또는 스코프 없음
            }
            if (isBlank(m.getDataEv()) || isBlank(m.getDataType())) {
                continue; // 안전장치
            }

            AffAdminDefaultTarget row = new AffAdminDefaultTarget();
            row.setEvalYear(year);
            row.setUserId(evaluatorId);
            row.setTargetId(t.getId());
            row.setEvalTypeCode(m.getEvalTypeCode()); // 예: SHEAD_TO_SMEMBER
            row.setDataEv(m.getDataEv()); // A/B/C/D
            row.setDataType(m.getDataType()); // AC/AD/AE
            cnt += defMap.upsert(row);
        }
        return cnt;
    }

    // =========================
    // 핵심: 대상 수집
    // =========================

    /** 규칙 기반 대상 수집 (모든 로직의 입구) */
    private List<UserPE> collectTargetsByRule(UserPE me, int year,
            RoleCache roleCache, SubCodeCache subCodeCache) {

        Set<String> roles = roleCache.rolesOf(me.getId());

        boolean isOrgHead = roles.contains("aff_org_head") || roles.contains("org_head");
        boolean isAgcHead = roles.contains("aff_agc_head") || roles.contains("agc_head");
        boolean isSubHead = roles.contains("aff_sub_head") || roles.contains("sub_head");
        boolean isSubMember = roles.contains("sub_member");

        // + 자야클린(c_name2) 기관은 부서원 매칭 제외
        String cName2 = me.getCName2();
        boolean isJayaCleanOrg = "자야클린".equals(cName2);
        
        List<UserPE> buf = new ArrayList<>();

        // 1) 기관장 역할: 각 병원의 '정성모아' 부서 전체
        if (isOrgHead) {
            buf.addAll(collectForOrgHead(me, year));
        }

        // 2) 소속장 역할: 병원인 경우 = 같은 병원 + c_name2='정성모아'
        if (isAgcHead) {
            buf.addAll(collectForAgcHead(me, year, subCodeCache, roleCache));
        }

        // 3) 부서장 역할
        if (isSubHead) {
            buf.addAll(collectForSubHead(me, year, subCodeCache, roleCache));
        }

        // 4) 일반 부서원
        // 상위 역할(기관장/소속장/부서장)이 없을 때만 동료/상향 평가

        if (isSubMember && !isOrgHead && !isAgcHead && !isSubHead && !isJayaCleanOrg) {
            buf.addAll(collectForSubMember(me, year, subCodeCache, roleCache));
        }

        if (buf.isEmpty()) {
            return List.of();
        }

        // id 기준 dedup + 자기자신 제거
        Map<String, UserPE> unique = new LinkedHashMap<>();
        for (UserPE u : buf) {
            if (u == null)
                continue;
            String id = nz(u.getId());
            if (id.isEmpty() || id.equals(me.getId()))
                continue;
            unique.putIfAbsent(id, u);
        }
        return new ArrayList<>(unique.values());
    }

    /** 기관장: 모든 병원의 부서명이 정성모아인 직원 대상 */
    private List<UserPE> collectForOrgHead(UserPE me, int year) {
        return userMapper.findBySubName("정성모아", year).stream()
                .filter(u -> notSelf(u, me))
                .toList();
    }

    // 시그니처 변경 (RoleCache 추가)
    private List<UserPE> collectForAgcHead(UserPE me,
            int year,
            SubCodeCache cache,
            RoleCache roleCache) {
        String org = nz(me.getCName());
        String group = nz(me.getCName2());

        List<UserPE> candidates;

        if (isHospital(org)) {
            // 같은 병원 + 정성모아
            candidates = userMapper.findByOrgAndGroup(org, "정성모아", year);
        } else {
            // 나머지 계열사는 기존대로 같은 기관+같은 소속
            candidates = userMapper.findByOrgAndGroup(org, group, year);
        }

        return candidates.stream()
                .filter(u -> notSelf(u, me))
                // 🔴 소속장은 소속장을 평가하지 않는다 → HEAD 대상 제거
                .filter(u -> {
                    Set<String> roles = roleCache.rolesOf(u.getId());
                    return !isHead(roles);
                })
                .toList();
    }

    /** 부서장 전용 대상 수집 */
    private List<UserPE> collectForSubHead(UserPE me,
            int year,
            SubCodeCache cache,
            RoleCache roleCache) {
        String org = nz(me.getCName());
        String group = nz(me.getCName2());
        String subName = ensureSubName(me, cache);
        String subCode = cache.subCodeOf(me.getId());

        List<UserPE> targets = List.of();

        if (isHospitalOrg(org)) {
            // 🔵 병원: c_name2(소속기관)는 무시하고,
            // 같은 기관(c_name) + 같은 sub_code 부서원 전체를 평가 대상으로.
            List<UserPE> sameDept = userMapper.findByOrgAndSubCode(org, subCode, year);
            targets = sameDept;

        } else if (isJayaclean(org)) {
            List<UserPE> all = userMapper.findByOrgAndGroup(org, group, year);
            targets = all;

        } else if (isCoreTalent(org)) {
            List<UserPE> all = userMapper.findByOrgAndGroup(org, group, year);
            targets = all;

        } else if (isJoy(org)) {
            // 🔴 주식회사 조이: 같은 소속기관 전체 + 다른 소속기관의 HEAD들
            List<UserPE> sameGroupAll = userMapper.findByOrgAndGroup(org, group, year);

            List<UserPE> allInOrg = userMapper.findByOrg(org, year); // 새로 추가할 mapper

            List<UserPE> otherHeads = allInOrg.stream()
                    .filter(u -> !nz(u.getCName2()).equals(group)) // 다른 소속기관
                    .filter(u -> isHead(roleCache.rolesOf(u.getId()))) // HEAD 역할
                    .toList();

            List<UserPE> buf = new ArrayList<>();
            buf.addAll(sameGroupAll);
            buf.addAll(otherHeads);
            targets = buf;

        } else if (isJaya(org) || isFuneral(org)) {
            List<UserPE> sameDept = !isBlank(subName)
                    ? userMapper.findByOrgAndSubName(org, subName, year)
                    : userMapper.findBySubCodeInOrg(subCode, year, org, group);
            targets = sameDept;

        } else {
            List<UserPE> sameDept = !isBlank(subName)
                    ? userMapper.findByOrgAndSubName(org, subName, year)
                    : userMapper.findBySubCodeInOrg(subCode, year, org, group);
            targets = sameDept;
        }

        return targets.stream()
                .filter(u -> notSelf(u, me))
                .toList();
    }

    /** 계열사 일반 구성원(SUB_MEMBER) 대상 수집 (기본형) */
    private List<UserPE> collectForSubMember(UserPE me,
            int year,
            SubCodeCache cache,
            RoleCache roleCache) {

        String org = nz(me.getCName());
        String group = nz(me.getCName2());
        String subName = ensureSubName(me, cache);
        String subCode = cache.subCodeOf(me.getId());
        String myId = me.getId();

        if (isHospital(org)) {
            return List.of();
        }

        // 🔴 Joy: 같은 소속기관 + 같은 부서끼리 상호 평가
        if (isJoy(org)) {
            List<UserPE> sameDept = userMapper.findByOrgGroupAndSubCode(org, group, subCode, year);

            return sameDept.stream()
                    .filter(u -> notSelf(u, me))
                    .toList();
        }

        // 🔴 핵심인재개발원: 동료 + 내 부서장
        if (isCoreTalent(org)) {
            List<UserPE> peers = (!isBlank(subName)
                    ? userMapper.findByOrgAndSubName(org, subName, year)
                    : userMapper.findBySubCodeInOrg(subCode, year, org, group))
                    .stream()
                    .filter(u -> notSelf(u, me))
                    .toList();

            UserPE head = peers.stream()
                    .filter(u -> isHead(roleCache.rolesOf(u.getId())))
                    .findFirst()
                    .orElse(null);

            List<UserPE> result = new ArrayList<>(peers);
            if (head != null && !myId.equals(head.getId())) {
                result.add(head); // 부서장 추가
            }
            return result;
        }

        // 기본: 같은 기관 + 같은 부서
        List<UserPE> sameDept = (!isBlank(subName)
                ? userMapper.findByOrgAndSubName(org, subName, year)
                : userMapper.findBySubCodeInOrg(subCode, year, org, group));

        return sameDept.stream()
                .filter(u -> notSelf(u, me))
                .toList();
    }

    // =========================
    // 메타 생성 / 스코프 / 폼코드
    // =========================

    /** 메타 생성: eval_type_code(축약) + data_ev + data_type(폼코드) */
    private EvalMeta buildMeta(UserPE evaluator, UserPE target, int year,
            RoleCache roleCache, SubCodeCache subCodeCache) {

        Set<String> eRoles = roleCache.rolesOf(evaluator.getId());
        Set<String> tRoles = roleCache.rolesOf(target.getId());

        boolean eHead = isHead(eRoles);
        boolean tHead = isHead(tRoles);

        Scope scope = decideScope(evaluator, target, subCodeCache);
        if (scope == Scope.NONE) {
            return null;
        }

        // eval_type_code: O/A/S + HEAD|MEMBER → ..._TO_...
        String evalType = compactType(scope, eHead, tHead);

        // A/B/C/D (상하/동료 방향)
        String dataEv = (!eHead && tHead) ? "C" : // 부서원 → 부서장
                (eHead && tHead) ? "A" : // 부서장 ↔ 부서장
                        (eHead && !tHead) ? "B" : // 부서장 → 부서원
                                "D"; // 부서원 ↔ 부서원

        // 🔴 폼코드: c_name, c_name2, dataEv 기준으로 결정
        String formCode = switchForm(
                nz(evaluator.getCName()), // org
                nz(evaluator.getCName2()), // org2 (소속기관)
                dataEv);
        if (formCode == null) {
            return null;
        }

        EvalMeta m = new EvalMeta();
        m.setEvalTypeCode(evalType);
        m.setDataEv(dataEv);
        m.setDataType(formCode);
        m.setFormId(null);
        return m;
    }

    private Scope decideScope(UserPE eval, UserPE tgt, SubCodeCache cache) {
        String eC = nz(eval.getCName()), tC = nz(tgt.getCName());
        String eG = nz(eval.getCName2()), tG = nz(tgt.getCName2());
        String eSN = ensureSubName(eval, cache), tSN = ensureSubName(tgt, cache);
        String eSC = cache.subCodeOf(eval.getId());
        String tSC = cache.subCodeOf(tgt.getId());

        boolean sameOrg = !eC.isEmpty() && eC.equals(tC);
        boolean sameAgc = !eG.isEmpty() && eG.equals(tG);
        boolean sameSub = (!eSN.isEmpty() && eSN.equals(tSN)) ||
                (!eSC.isEmpty() && eSC.equals(tSC));

        if (sameSub)
            return Scope.SUB;
        if (sameAgc)
            return Scope.AGC;
        if (sameOrg)
            return Scope.ORG;
        return Scope.NONE;
    }

    private static String compactType(Scope s, boolean eHead, boolean tHead) {
        String p = switch (s) {
            case ORG -> "O";
            case AGC -> "A";
            case SUB -> "S";
            default -> null;
        };
        if (p == null)
            return null;

        String L = eHead ? "HEAD" : "MEMBER";
        String R = tHead ? "HEAD" : "MEMBER";
        return p + L + "_TO_" + p + R;
    }

    private static boolean isHead(Set<String> roles) {
        if (roles == null || roles.isEmpty())
            return false;

        for (String r : roles) {
            if (r == null)
                continue;
            String v = r.toLowerCase(Locale.ROOT);
            if (v.contains("head")) { // aff_org_head, org_head, aff_sub_head, sub_head 등 모두 커버
                return true;
            }
        }
        return false;
    }

    // 🔴 새 매핑 규칙 적용
    // org = c_name
    // org2 = c_name2
    // dataEv = A/B/C/D
    private static String switchForm(String org, String org2, String dataEv) {
        String ev = nz(dataEv);

        // 1) 병원/사랑모아/정성모아/자야클린 계열
        if (isHospitalLike(org, org2)) {
            // A/C -> AC
            if ("A".equals(ev) || "C".equals(ev)) {
                return "AC";
            }
            // B/D -> AD
            if ("B".equals(ev) || "D".equals(ev)) {
                return "AD";
            }
            return null;
        }

        // 2) 나머지(주식회사 조이, 핵심인재개발원, 자야, 장례문화원 등)
        // - A/C -> AC
        // - B/D -> AE
        if ("A".equals(ev) || "C".equals(ev)) {
            return "AC";
        }
        if ("B".equals(ev) || "D".equals(ev)) {
            return "AE";
        }

        return null;
    }

    // =========================
    // 캐시 & 유틸
    // =========================

    /** 병원 여부(c_name 기준) – 정확 일치 */
    private static boolean isHospitalOrg(String org) {
        return HOSPITAL_ORGS.contains(org);
    }

    /** 병원 여부(c_name 부분 포함 체크) */
    private boolean isHospital(String cName) {
        String v = nz(cName);
        return v.contains("효사랑전주요양병원")
                || v.contains("효사랑가족요양병원")
                || v.contains("가족사랑요양병원");
    }

    private boolean isJayaclean(String org) {
        return "자야클린".equals(org);
    }

    private static boolean isCoreTalent(String org) {
        return "핵심인재개발원".equals(org);
    }

    private static boolean isJoy(String org) {
        return "주식회사 조이".equals(org);
    }

    private static boolean isJaya(String org) {
        return "자야".equals(org);
    }

    private static boolean isFuneral(String org) {
        return "자야장례문화원".equals(org);
    }

    /** 두 User가 "같은 부서"인지 (sub_name 또는 sub_code 기준) */
    private static boolean sameSubDept(UserPE a, UserPE b, SubCodeCache cache) {
        String aSN = ensureSubName(a, cache);
        String bSN = ensureSubName(b, cache);
        String aSC = cache.subCodeOf(a.getId());
        String bSC = cache.subCodeOf(b.getId());

        boolean byName = !isBlank(aSN) && aSN.equals(bSN);
        boolean byCode = !isBlank(aSC) && aSC.equals(bSC);
        return byName || byCode;
    }

    /** 역할 캐시 */
    private static class RoleCache {
        private final AffUserRolesMapper mapper;
        private final int year;
        private final Map<String, Set<String>> cache = new HashMap<>();

        RoleCache(AffUserRolesMapper mapper, int year) {
            this.mapper = mapper;
            this.year = year;
        }

        Set<String> rolesOf(String userId) {
            return cache.computeIfAbsent(userId,
                    k -> lower(mapper.findRolesByUser(k, year)));
        }
    }

    /** sub_code 캐시 + sub_name 보강 */
    private static class SubCodeCache {
        private final AffUserMapper mapper;
        private final int year;
        private final Map<String, String> subCodeByUser = new HashMap<>();
        private final Map<String, String> subNameByCode = new HashMap<>();

        SubCodeCache(AffUserMapper mapper, int year) {
            this.mapper = mapper;
            this.year = year;
        }

        String subCodeOf(String userId) {
            return subCodeByUser.computeIfAbsent(
                    userId,
                    id -> nz(mapper.findSubCodeByUser(id, year)));
        }

        String subNameOfCode(String subCode) {
            if (isBlank(subCode))
                return "";
            return subNameByCode.computeIfAbsent(
                    subCode,
                    sc -> nz(mapper.findSubNameByCode(sc, year)));
        }
    }

    /** UserPE에 subName이 비어있으면 sub_code→sub_name으로 보강 */
    private static String ensureSubName(UserPE u, SubCodeCache cache) {
        String name = nz(u.getSubName());
        if (!name.isEmpty())
            return name;
        String code = cache.subCodeOf(u.getId());
        return cache.subNameOfCode(code);
    }

    private static boolean notSelf(UserPE u, UserPE me) {
        return u != null && u.getId() != null && !u.getId().equals(me.getId());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static Set<String> lower(List<String> roles) {
        if (roles == null)
            return Set.of();
        return roles.stream()
                .filter(Objects::nonNull)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    // =========================
    // 화면용 헬퍼 (기존 유지)
    // =========================

    @Transactional(readOnly = true)
    private List<UserPE> computeFinalTargetsWithMeta(String evaluatorId, int year) {
        UserPE me = userMapper.findById(evaluatorId, year);
        if (me == null)
            return List.of();

        RoleCache roleCache = new RoleCache(rolesMapper, year);
        SubCodeCache subCodeCache = new SubCodeCache(userMapper, year);

        List<UserPE> candidates = collectTargetsByRule(me, year, roleCache, subCodeCache);
        if (candidates == null || candidates.isEmpty())
            return List.of();

        List<UserPE> result = new ArrayList<>();
        for (UserPE t : candidates) {
            EvalMeta m = buildMeta(me, t, year, roleCache, subCodeCache);
            if (m == null)
                continue;
            if (isBlank(m.getDataEv()) || isBlank(m.getDataType()))
                continue;

            UserPE u = new UserPE();
            u.setId(t.getId());
            u.setName(t.getName());
            u.setPosition(t.getPosition());
            u.setCName(t.getCName());
            u.setCName2(t.getCName2());

            String subCode = subCodeCache.subCodeOf(t.getId());
            u.setSubCode(subCode);
            String subName = ensureSubName(t, subCodeCache);
            u.setSubName(subName);
            u.setTeamCode(t.getTeamCode());
            u.setTeamName(t.getTeamName());

            u.setEvalTypeCode(m.getEvalTypeCode());
            u.setDataEv(m.getDataEv());
            u.setDataType(m.getDataType());

            result.add(u);
        }
        return result;
    }

    private Map<String, List<UserPE>> groupByEvalType(List<UserPE> users) {
        Map<String, List<UserPE>> grouped = new LinkedHashMap<>();
        getTypeLabels().keySet().forEach(k -> grouped.put(k, new ArrayList<>()));

        if (users == null)
            return grouped;

        for (UserPE u : users) {
            if (u == null)
                continue;
            String key = u.getEvalTypeCode();
            if (isBlank(key))
                key = "UNKNOWN";
            grouped.computeIfAbsent(key, __ -> new ArrayList<>()).add(u);
        }

        return grouped;
    }

    public Map<String, List<UserPE>> getFinalTargetsGroupedByType(String employeeId, int year) {
        List<UserPE> targets = computeFinalTargetsWithMeta(employeeId, year);
        return groupByEvalType(targets);
    }

    public Map<String, String> getTypeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();

        // SUB(부서) 스코프
        labels.put("SHEAD_TO_SMEMBER", "부서장 → 부서원");
        labels.put("SMEMBER_TO_SHEAD", "부서원 → 부서장");
        labels.put("SMEMBER_TO_SMEMBER", "부서원 ↔ 부서원");
        labels.put("SHEAD_TO_SHEAD", "부서장 ↔ 부서장");

        // AGC(소속) 스코프
        labels.put("AHEAD_TO_AMEMBER", "소속장 → 소속 구성원");
        labels.put("AMEMBER_TO_AHEAD", "소속 구성원 → 소속장");
        labels.put("AMEMBER_TO_AMEMBER", "소속 구성원 ↔ 소속 구성원");
        labels.put("AHEAD_TO_AHEAD", "소속장 ↔ 소속장");

        // ORG(기관) 스코프
        labels.put("OHEAD_TO_OMEMBER", "기관장 → 기관 구성원");
        labels.put("OMEMBER_TO_OHEAD", "기관 구성원 → 기관장");
        labels.put("OMEMBER_TO_OMEMBER", "기관 구성원 ↔ 기관 구성원");
        labels.put("OHEAD_TO_OHEAD", "기관장 ↔ 기관장");

        labels.putIfAbsent("UNKNOWN", "기타");

        return labels;
    }

    @Transactional(readOnly = true)
    public SplitTargets splitTargets(String employeeId, int year) {
        List<UserPE> defaults = computeFinalTargetsWithMeta(employeeId, year);

        SplitTargets dto = new SplitTargets();
        dto.setDefaultOnly(defaults);
        dto.setCustomOnly(List.of());
        dto.setOverlap(List.of());
        dto.setDefaultGrouped(groupByEvalType(defaults));
        dto.setCustomGrouped(new HashMap<>());

        return dto;
    }
}
