(function () {
    // 모달/스피너를 레이아웃 밖(바디 직속)으로 이동 → stacking/overflow 영향 제거
    ['spinner-overlay', 'modal-info', 'modal-confirm'].forEach(id => {
        const el = document.getElementById(id);
        if (el && el.parentElement !== document.body) document.body.appendChild(el);
    });
    'use strict';

    // 필수 의존성 확인
    if (typeof window.jQuery === 'undefined') {
        console.error('[eval.js] jQuery가 필요합니다.');
        return;
    }
    const $ = window.jQuery;

    // 서버 주입 값 읽기 (기본값도 지정)
    const CFG = window.__PE_CONFIG || {};
    const {
        DATA_TYPE = 'AA',
        DATA_EV = 'C',
        YEAR = '2025',
        EVALUATOR = '00000000',
        TARGET_ID = '000000',
        TARGET_NAME = '홍길동',
        ACTION_URL = '/aff/api/formAction',
        INFO_URL = '/aff/info',
        END_URL = '/aff/FormEnd'
    } = CFG;

    // 전역 에러 로깅(디버깅 편의)
    window.onerror = function (msg, src, line, col, err) {
        console.error('[eval.js] JS Error:', msg, src, `${line}:${col}`, err);
    };

    // ====== UI 유틸 ======
    function spinner(show = true) {
        const el = document.getElementById('spinner-overlay');
        if (!el) return;

        if (show) {
            el.style.display = 'flex';
            requestAnimationFrame(() => el.classList.add('show'));
        } else {
            el.classList.remove('show');
            const onEnd = (e) => {
                if (e.target !== el) return;
                el.style.display = 'none';
                el.removeEventListener('transitionend', onEnd);
            };
            el.addEventListener('transitionend', onEnd);
        }
    }

    function showInfo(msg) {
        console.log('[showInfo] open');
        $('#modal-info-text').html(msg);
        $('#modal-info').addClass('show');   // ★ 이 줄 추가
        $('body').css('overflow', 'hidden');
    }
    function hideInfo() {
        $('#modal-info').removeClass('show'); // ★ 이 줄 추가
        $('body').css('overflow', 'auto');
    }

    function confirmModal(msg, onOk) {
        console.log('[confirmModal] open');
        $('#modal-confirm-text').html(msg);
        $('#modal-confirm').addClass('show'); // ★ 이 줄 추가
        $('body').css('overflow', 'hidden');

        $('#modal-confirm-ok').off('click.pe').on('click.pe', function () {
            $('#modal-confirm').removeClass('show'); // ★ 이 줄 추가
            $('body').css('overflow', 'auto');
            onOk && onOk();
        });
        $('#modal-confirm-cancel').off('click.pe').on('click.pe', function () {
            $('#modal-confirm').removeClass('show'); // ★ 이 줄 추가
            $('body').css('overflow', 'auto');
        });
    }

    // ====== 비즈 로직 ======
    function mapScore(val) {
        switch (val) {
            case '매우우수': return 5;
            case '우수': return 4;
            case '보통': return 3;
            case '미흡': return 2;
            case '매우미흡': return 1;
            default: return 0;
        }
    }

    function computeAndValidate() {
    let total = 0;
    const missing = [];

    // 객관식 그룹핑
    const radiosByName = {};
    $('input.radio[type=radio]').each(function () {
        (radiosByName[this.name] ||= []).push(this);
    });

    Object.keys(radiosByName).forEach(function (nm) {
        const $group = $(`input[name="${nm}"]`);
        const $checked = $group.filter(':checked');

        const title = $group.first()
            .closest('.question-area')
            .find('p.question')
            .text()
            .trim() || `문항(${nm})`;

        if ($checked.length === 0) {
            missing.push(title);
        } else {
            let base = mapScore($checked.val());

            // 🔴 여기만 수정
            // 기존: if (['AA','AC','AD','AE'].includes(DATA_TYPE)) base *= 2;
            if (DATA_TYPE === 'AC') {
                base *= 2;   // AC일 때만 10점 만점(5점*2)
            }

            total += base;
        }
    });

    // 주관식 필수 체크 그대로 유지
    $('textarea[name^="t"]').each(function () {
        if (!$(this).val().trim()) {
            const title = $(this)
                .closest('table')
                .find('p.question-text')
                .text()
                .trim() || '주관식';
            missing.push(title);
        }
    });

    return { ok: missing.length === 0, missing, total };
}

    // ====== 이벤트 바인딩 ======
    $(function () {
        // 안내 모달 닫기
        $('#modal-info-close').off('click').on('click', hideInfo);
        // 1) 디버그 핸들러: peDebug 네임스페이스
        $('#end').off('click.peDebug').on('click.peDebug', function () {
            const r = computeAndValidate();
            console.log('[end debug] ok:', r.ok, 'missing:', r.missing);
        });

        // 2) 메인 핸들러: pe 네임스페이스 (다른 건 지우지 않음)
        $('#end').off('click.pe').on('click.pe', function (e) {
            console.log('[end main] start');
            const { ok, missing, total } = computeAndValidate();
            console.log('[end main] validate:', ok, missing, total);

            // 변경 (간단 안내만)
            if (!ok) {
                showInfo('<p>현재 답변하지 않은 항목이 있습니다.</p><p>모든 문항에 응답 후 다시 시도해주세요.</p>');
                return;
            }

            console.log('[end main] confirm open');
            confirmModal(
                `<p><strong>${TARGET_NAME}</strong> 님의 평가 예상점수는 <strong>${total}점</strong>입니다.</p><p>평가완료 하시겠습니까?</p>`,
                function () {
                    console.log('[end main] confirm OK → AJAX');
                    spinner(true);
                    const formData = $('#frm').serializeArray();
                    formData.push({ name: 'score', value: total });
                    formData.push({ name: 'year', value: YEAR });
                    $.ajax({
                        url: ACTION_URL, type: 'POST', dataType: 'json', data: formData,
                        success: function (res) {
                            spinner(false);
                            console.log('[end main] ajax success:', res);
                            const code = res && res.result;
                            if (code === '1') {
                                showInfo('<p>이미 평가가 완료된 대상입니다.</p><p>마이페이지로 이동합니다.</p>');
                                $('#modal-info-close').off('click.pe').on('click.pe', function () {
                                    window.location.href = INFO_URL;
                                });
                            } else if (code === '2') {
                                window.location.href = END_URL;
                            } else {
                                showInfo('<p>평가 완료 중 오류가 발생했습니다.</p><p>다시 시도해주세요.</p>');
                            }
                        },
                        error: function (err) {
                            spinner(false);
                            console.error('[end main] ajax error:', err);
                            showInfo('<p>평가 완료 중 오류가 발생했습니다.</p><p>다시 시도해주세요.</p>');
                        }
                    });
                }
            );
        });

        // 상단 안내 고정
        (function () {
            const targetInfo = document.getElementById('targetInfo');
            if (!targetInfo) return;
            const initial = 207;
            window.addEventListener('scroll', () => {
                const y = window.scrollY || window.pageYOffset;
                if (y >= initial) targetInfo.classList.add('fixed');
                else targetInfo.classList.remove('fixed');
            });
        })();
    });

    $('.menu1').addClass('active');
})();