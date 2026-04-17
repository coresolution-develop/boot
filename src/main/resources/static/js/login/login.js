// ===== 모바일 판별(터치/UA 둘 다 체크) =====
const isMobile = window.matchMedia('(pointer: coarse)').matches ||
                 /Mobi|Android|iPhone|iPad|iPod/i.test(navigator.userAgent);

// ===== 라디오(이름/비밀번호) 초기 세팅 =====
$(function () {
  // URL 파라미터로 chk_no=checked 오면 비밀번호 모드로
  const param = new URLSearchParams(location.search);
  if (param.get('chk_no') === 'checked') {
    $('#chk_no').prop('checked', true);
  }

  // 공통 전환 함수: 필요 시에만 focus
  function switchMode(toPwd, { doFocus = true } = {}) {
    const $pwd = $('#pwd');
    if (toPwd) {
      $pwd.attr({ placeholder: '비밀번호', type: 'password', title: '비밀번호입력', name: 'pwd' })
          .removeClass('name').addClass('pwd');
    } else {
      $pwd.attr({ placeholder: '이름', type: 'text', title: '이름입력', name: 'name' })
          .removeClass('pwd').addClass('name');
    }
    if (doFocus && !isMobile) $pwd.focus();   // ← 모바일이면 초기 포커스 금지
  }

  // 라디오 전환
  $('#chk_name').on('click', () => switchMode(false));        // 사용자 클릭 → 포커스 허용
  $('#chk_no').on('click',   () => switchMode(true));

  // 최초 상태 적용(자동 포커스 금지)
  if ($('#chk_no').is(':checked')) switchMode(true,  { doFocus:false });
  else                             switchMode(false, { doFocus:false });

  // 혹시 다른 곳에서 자동 포커스가 걸렸다면 한 번 더 제거
  if (isMobile) {
    // iOS 대비: pageshow 시점에도 한 번 더 블러
    window.addEventListener('pageshow', () => setTimeout(() => document.activeElement?.blur(), 0), { once:true });
  }


  // 에러 파라미터 처리 → 모달
  const params = new URLSearchParams(location.search);
  if (params.has('error')) {
    const code = params.get('error');
    let message;
    switch (code) {
      case '0': message = '2025년도 직원근무평가 대상직원이 아닙니다.'; break;
      case '1': message = '비밀번호가 일치하지 않습니다.'; break;
      case '2': message = '현재 비밀번호가 설정되어 있습니다. 비밀번호로 로그인 해주세요.'; break;
      case '3': message = '이름이 일치하지 않습니다.'; break;
      case '5': {
        message = '현재 비밀번호가 설정되어 있지 않습니다. 비밀번호 설정 페이지로 이동합니다.';
        const idx = params.get('idx');
        $('#modalOkBtn').attr('onclick', `location.href='/pwd/${idx}'`);
        break;
      }
      default: message = '로그인 처리 중 오류가 발생했습니다.';
    }
    openPopup(message);
  }
});

// ===== 모달 유틸 =====
// 공용 show/hide 유틸
function showModal(selector, html) {
  const modal = document.querySelector(selector);
  if (!modal) return;
  if (html) {
    const box = modal.querySelector('.menu_msg, .menu_msg2, .menu_msg3, .menu_msg4');
    if (box) box.innerHTML = `<p>${html}</p>`;
  }
  document.body.style.overflow = 'hidden';
  modal.classList.add('show');
}
function hideModal(selector) {
  const modal = document.querySelector(selector);
  if (!modal) return;
  modal.classList.remove('show');
  document.body.style.overflow = 'auto';
}

// ===== 로그인 제출 (엔터/클릭 모두 여기로) =====
(function () {
  const form = document.getElementById('login_form');
  let submitting = false;

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (submitting) return;
    submitting = true;

    try {
      const res = await fetch(form.action, {
        method: 'POST',
        body: new FormData(form),
        headers: {
          // AJAX 인지 신호: 서버 성공핸들러/실패핸들러가 JSON으로 응답
          'X-Requested-With': 'XMLHttpRequest',
          'Accept': 'application/json'
        },
        // 같은 오리진이면 credentials 기본값으로도 충분. 필요시:
        // credentials: 'same-origin'
      });

      if (res.ok) {
        // 서버 성공 핸들러가 내려준 redirect 목적지로 이동
        // 예: {"ok":true,"redirect":"/Info/1298"}
        const data = await res.json();
        const to = (data && data.redirect) ? data.redirect : '/';
        window.location.assign(to);              // ✅ 올바른 호출
        return;
      }

      if (res.status === 401) {
        const data = await res.json().catch(() => ({}));

        let message = '아이디 또는 비밀번호가 올바르지 않습니다.';

        // 🔴 평가 대상자가 아닌 경우(del_yn = 'Y')
        if (data && data.result === '0') {
          message = data.message || '2025년도 직원근무평가 대상직원이 아닙니다.';
        }
        // 🔵 비밀번호가 이미 설정된 경우 (사번+이름 로그인 불가)
        else if (data && data.result === '2') {
          message = '현재 비밀번호가 설정되어 있습니다. 비밀번호로 로그인 해주세요.';
          // 필요하면 여기서 라디오를 비밀번호 모드로 전환해도 됨:
          // document.getElementById('chk_no')?.click();
        }
        // 비밀번호 미설정 케이스 (기존 로직)
        else if (data && data.result === '5' && data.idx) {
          message = '현재 비밀번호가 설정되어 있지 않습니다. 비밀번호 설정 페이지로 이동합니다.';
          document.getElementById('modalOkBtn')
            ?.setAttribute('onclick', `location.href='/pwd/${data.idx}'`);
        }

        openPopup(message);
        return;
      }

      openPopup('잠시 후 다시 시도해주세요.');
    } catch (err) {
      openPopup('네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      submitting = false;
    }
  });
})();


// 기존 HTML의 onclick과 1:1 매핑 (전역 노출)
window.closePopup  = () => hideModal('.modal');
window.closePopup2 = () => hideModal('.modal2');
window.closePopup3 = () => hideModal('.modal3');
window.closePopup4 = () => hideModal('.modal4');

// 모달 열 때 재사용할 전역 함수(필요시)
window.openPopup  = (html) => showModal('.modal', html);
window.openPopup2 = (html) => showModal('.modal2', html);
window.openPopup3 = (html) => showModal('.modal3', html);
window.openPopup4 = (html) => showModal('.modal4', html);
