(function () {
    // 고정 헤더/네비/고정 알림바 높이 보정
    function getFixedOffset() {
        const h = document.querySelector('header');
        const n = document.querySelector('.nav_section2, nav');
        const info = document.getElementById('targetInfo');

        let off = 0;
        off += h?.offsetHeight || 0;
        off += n?.offsetHeight || 0;
        if (info) {
            const cs = getComputedStyle(info);
            if (cs.position === 'fixed') off += info.offsetHeight || 0;
        }
        return off + 8; // 여유
    }

    // window 기준 스크롤
    function scrollToSection(selector) {
        const el = document.querySelector(selector);
        if (!el) return;
        const offset = getFixedOffset();
        const top = el.getBoundingClientRect().top + window.pageYOffset - offset;
        window.scrollTo({ top, behavior: 'smooth' });
    }

    // 버튼-섹션 매핑
    let MAP = [
        ['#title1btn', '#sec-medical'], // 진료부
        ['#title2btn', '#sec-gh'],      // 경혁팀
        ['#title3btn', '#sec-heads'],   // 부서장
        ['#title4btn', '#sec-members']  // 부서원
    ]
        // 실제로 존재하는 것만 사용
        .filter(([b, s]) => document.querySelector(b) && document.querySelector(s))
        // 섹션의 문서 상 Y 위치 기준으로 정렬
        .sort((a, b) => {
            const ta = document.querySelector(a[1]).getBoundingClientRect().top + window.pageYOffset;
            const tb = document.querySelector(b[1]).getBoundingClientRect().top + window.pageYOffset;
            return ta - tb;
        });

    function setActiveBySection(secSel) {
        MAP.forEach(([btnSel]) => document.querySelector(btnSel)?.classList.remove('act'));
        const ent = MAP.find(([, s]) => s === secSel);
        if (ent) document.querySelector(ent[0])?.classList.add('act');
    }

    // 스크롤 위치로 활성 버튼 갱신
    function updateActiveByScroll() {
        if (MAP.length === 0) return;
        const offsetTop = window.pageYOffset + getFixedOffset() + 1;

        // 현재 뷰포트 상단(보정 포함) 기준, top <= 기준인 마지막 섹션을 활성화
        let current = MAP[0];
        for (const pair of MAP) {
            const sec = document.querySelector(pair[1]);
            const top = sec.getBoundingClientRect().top + window.pageYOffset;
            if (top <= offsetTop) current = pair; else break;
        }
        setActiveBySection(current[1]);
    }

    // 버튼 바인딩
    MAP.forEach(([btnSel, secSel]) => {
        const btn = document.querySelector(btnSel);
        btn.setAttribute('role', 'button');
        btn.setAttribute('tabindex', '0');
        btn.addEventListener('click', () => {
            setActiveBySection(secSel);   // 즉시 반영
            scrollToSection(secSel);      // 스크롤
        });
        btn.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); btn.click(); }
        });
    });

    // 스크롤/리사이즈에서 부드럽게 갱신 (rAF로 스로틀)
    let raf = 0;
    function onScrollOrResize() {
        if (raf) return;
        raf = requestAnimationFrame(() => { raf = 0; updateActiveByScroll(); });
    }
    window.addEventListener('scroll', onScrollOrResize, { passive: true });
    window.addEventListener('resize', () => {
        // 레이아웃이 변하면 섹션 위치 재정렬
        MAP = MAP
            .filter(([b, s]) => document.querySelector(b) && document.querySelector(s))
            .sort((a, b) => {
                const ta = document.querySelector(a[1]).getBoundingClientRect().top + window.pageYOffset;
                const tb = document.querySelector(b[1]).getBoundingClientRect().top + window.pageYOffset;
                return ta - tb;
            });
        onScrollOrResize();
    });

    // 초기 활성화
    updateActiveByScroll();
    

    $('.menu1').addClass('active');
})();