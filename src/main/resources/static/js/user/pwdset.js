$(document).ready(function () {
    const body = document.querySelector('body');
    const modal = document.querySelector('.modal');
    const msg = document.querySelector('.menu_msg');
    const icon = document.querySelector('.menu_icon');
    const btn1 = document.querySelector('.btn');
    const idx = $("#idx").val(); // idx hidden input 또는 서버에서 렌더링된 값

    $("#btn").click(function () {
        const pwd = $("#pwd").val();
        const pwd2 = $("#pwd2").val();

        if (!pwd || !pwd2) {
            openModal('<p>비밀번호를 입력해주세요.</p>');
            return;
        }
        if (pwd !== pwd2) {
            openModal('<p>비밀번호가 일치하지 않습니다.</p>');
            return;
        }

        $.ajax({
            type: 'POST',
            url: '/user/pwdAction',   // ★ idx 제거
            dataType: 'json',
            data: { pwd, pwd2 },
            success: function (response) {
                const res = response.result;
                if (res && res !== 'ok' && !response.redirectUrl) {
                    openModal(`<p>${res}</p>`);
                    return;
                }
                // 성공
                openModal('<p>비밀번호가 변경되었습니다.</p>');
                setTimeout(function () {
                    // 바꾸고 바로 마이페이지로 이동
                    window.location.href = response.redirectUrl || '/info';
                }, 1200);
            },
            error: function (err) {
                console.error('Error : ', err);
                openModal('<p>비밀번호 변경 처리 중 오류가 발생했습니다.</p><p>다시 시도해주세요.</p>');
            }
        });
    });
    $('.sub1').addClass('active');
    function openModal(html) {
        modal.classList.add('show');
        icon.style.top = '15%';
        msg.style.top = '30%';
        btn1.style.top = '40%';
        msg.innerHTML = html;
    }
});

function closePopup() {
    document.querySelector('.modal').classList.toggle('show');
    document.body.style.overflow = 'auto';
}
