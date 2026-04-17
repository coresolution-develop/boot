$(document).ready(function () {
    const body = document.querySelector('body');
    const modal = document.querySelector('.modal');
    const msg = document.querySelector('.menu_msg');
    const icon = document.querySelector('.menu_icon');
    const btn1 = document.querySelector('.btn');
    const idx = $("#idx").val(); // idx hidden input 또는 서버에서 렌더링된 값

    $("#btn").click(function(){
        var pwd = $("#pwd").val();
        var pwd2 = $("#pwd2").val();

        if(pwd === "" || pwd2 === ""){
            modal.classList.toggle('show');
            icon.style.top = '20%';
            msg.style.top = '34%';
            msg.innerHTML = '<p>비밀번호를 입력해주세요.</p>';
            return false;
        } else if(pwd !== pwd2){
            modal.classList.toggle('show');
            icon.style.top = '20%';
            msg.style.top = '34%';
            msg.innerHTML = '<p>비밀번호가 일치하지 않습니다.</p>';
            return false;
        } else {
            $.ajax({
                type: 'post',
                url: '/aff/pwdAction/' + idx,
                datatype: 'json',
                data: { "pwd": pwd,
                        "pwd2": pwd2 },
                success: function (response) {
                    var res = response.result;
                    if (res === "비밀번호가 일치하지 않습니다.") {
                        modal.classList.toggle('show');
                        icon.style.top = '15%';
                        msg.style.top = '30%';
                        btn1.style.top = '40%';
                        msg.innerHTML = '<p>비밀번호가 일치하지 않습니다.</p>';
                    } else if (res === "사용자를 찾을 수 없습니다.") {
                        modal.classList.toggle('show');
                        icon.style.top = '15%';
                        msg.style.top = '30%';
                        btn1.style.top = '40%';
                        msg.innerHTML = '<p>사용자를 찾을 수 없습니다.</p>';
                    } else if (res === "이미 비밀번호가 설정되어 있습니다.") {
                        modal.classList.toggle('show');
                        icon.style.top = '15%';
                        msg.style.top = '30%';
                        btn1.style.top = '40%';
                        msg.innerHTML = '<p>이미 비밀번호가 설정되어 있습니다.</p>';
                    } else if (response.redirectUrl) {
                        // 성공 시 안내 후 info 페이지로 이동
                        modal.classList.toggle('show');
                        icon.style.top = '15%';
                        msg.style.top = '30%';
                        btn1.style.top = '40%';
                        msg.innerHTML = '<p>비밀번호 설정이 완료되었습니다.</p><p>설정한 비밀번호로 로그인 해주세요.</p>';
                        setTimeout(function() {
                            window.location.href = response.redirectUrl;
                        }, 1500);
                    }
                },
                error: function (error) {
                    console.log('Error fetching data : ', error);
                    modal.classList.toggle('show');
                    icon.style.top = '15%';
                    msg.style.top = '30%';
                    btn1.style.top = '40%';
                    msg.innerHTML = '<p>비밀번호 변경 처리 중 오류가 발생했습니다.</p><p>다시 시도해주세요.</p>';
                }
            });
        }
    });
});

function closePopup(){
    document.querySelector('.modal').classList.toggle('show');
    document.body.style.overflow = 'auto';
}


function link() {
	var link = "/aff/Login?chk_no=checked";
	location.href = link;
}

function homego() {
	location.href='/aff/Login';
}
