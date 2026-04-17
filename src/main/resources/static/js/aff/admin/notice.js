(() => {
  const API = {
    list:   '/aff/admin/notices',          // GET : 목록(JSON)
    create: '/aff/admin/notices',          // POST: 생성(JSON)
    update: id => `/aff/admin/notices/${id}`, // PUT : 수정
    remove: id => `/aff/admin/notices/${id}`  // DELETE
  };

  const $overlay = $('#spinner-overlay');
  const showSpin = () => $overlay.show();
  const hideSpin = () => $overlay.hide();

  const $tblBody = $('#noticeTable tbody');
  const $year = $('#f-year'), $active = $('#f-active'), $pinned = $('#f-pinned');

  // --- 목록 렌더
  async function loadList(){
    showSpin();
    try{
      const res = await fetch(API.list, { headers: { 'Accept':'application/json' } });
      if(!res.ok){
        const text = await res.text();
        console.error('GET /aff/admin/notices failed', res.status, text);
        alert('목록 조회 실패: ' + res.status);
        $tblBody.html(`<tr><td colspan="9" style="text-align:center;color:#6b7280;">에러(${res.status})</td></tr>`);
        return;
      }
      const data = await res.json();

      // 필터
      const y = parseInt($year.val()||'0',10);
      const onlyActive = $active.is(':checked');
      const onlyPinned = $pinned.is(':checked');

      const rows = (Array.isArray(data) ? data : [])
        .filter(n => (y===0 || n.evalYear===y))
        .filter(n => (!onlyActive || !!n.isActive))
        .filter(n => (!onlyPinned || !!n.pinned))
        .sort((a,b)=>{
          // 서버 정렬이 있더라도 클라에서 한번 더 안전 정렬
          if (a.pinned !== b.pinned) return (a.pinned? -1 : 1);
          if (a.sortOrder !== b.sortOrder) return a.sortOrder - b.sortOrder;
          return (b.id - a.id);
        });

      $tblBody.empty();
      if(rows.length===0){
        $tblBody.append(`<tr><td colspan="9" style="text-align:center;color:#6b7280;">데이터가 없습니다.</td></tr>`);
        return;
      }

      for(const n of rows){
        const period = [fmtDT(n.publishFrom), fmtDT(n.publishTo)].filter(Boolean).join(' ~ ');
        $tblBody.append(`
          <tr data-id="${n.id}">
            <td>${n.id}</td>
            <td>${n.evalYear ?? ''}</td>
            <td class="ellipsis" title="${escapeHtml(n.title||'')}">${escapeHtml(n.title||'')}</td>
            <td>${n.versionTag ?? ''}</td>
            <td>${n.pinned ? '●' : ''}</td>
            <td>${n.sortOrder ?? ''}</td>
            <td>${period || ''}</td>
            <td>${n.isActive ? '활성' : '비활성'}</td>
            <td>
              <button class="btn small js-edit">수정</button>
              <button class="btn small js-preview">미리보기</button>
              <button class="btn small danger js-del">삭제</button>
            </td>
          </tr>
        `);
      }
    } finally {
      hideSpin();
    }
  }

  // --- 유틸
  function fmtDT(x){
    if(!x) return '';
    // x가 "2025-10-27T12:00:00" 혹은 "2025-10-27 12:00:00" 형태 가정
    return String(x).replace('T',' ').slice(0,16);
  }
  function toInputDT(x){
    if(!x) return '';
    const s = String(x).replace(' ','T');
    // yyyy-MM-ddThh:mm 형태만 취함
    return s.length>=16 ? s.slice(0,16) : s;
  }
  function escapeHtml(s){ return (s||'').replace(/[&<>"']/g, m=>({ '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;', "'":'&#39;' }[m])); }

  // --- 편집 모달
  const $modal = $('#noticeEditModal');
  const $form  = $('#noticeForm');
  const $title = $('#pvTitle');
  const $body  = $('#pvBody');
  const $btnDelete = $('#btn-delete');

  function openModal(){ $modal.addClass('show').show(); $('html,body').css('overflow','hidden'); }
  function closeModal(){ $modal.removeClass('show').hide(); $('html,body').css('overflow',''); }

  function fillForm(n){
    // n 없으면 신규
    $form[0].reset();
    $form.find('[name=id]').val(n?.id ?? '');
    $form.find('[name=evalYear]').val(n?.evalYear ?? 0);
    $form.find('[name=versionTag]').val(n?.versionTag ?? '');
    $form.find('[name=sortOrder]').val(n?.sortOrder ?? 10);
    $form.find('[name=pinned]').prop('checked', !!n?.pinned);
    $form.find('[name=isActive]').prop('checked', n?.isActive ?? true);
    $form.find('[name=publishFrom]').val(toInputDT(n?.publishFrom));
    $form.find('[name=publishTo]').val(toInputDT(n?.publishTo));
    $form.find('[name=title]').val(n?.title ?? '');
    $form.find('[name=bodyMd]').val(n?.bodyMd ?? '');

    // 미리보기
    renderPreview();
    $('#editorTitle').text(n?.id ? `공지 수정 #${n.id}` : '공지 작성');
    $btnDelete.toggle(!!n?.id);
  }

  function serializeForm(){
    const fd = new FormData($form[0]);
    const data = Object.fromEntries(fd.entries());
    data.evalYear = parseInt(data.evalYear||'0',10);
    data.sortOrder = parseInt(data.sortOrder||'0',10);
    data.pinned = !!fd.get('pinned');
    data.isActive = !!fd.get('isActive');
    data.publishFrom = data.publishFrom || null;
    data.publishTo   = data.publishTo   || null;
    return data;
  }

  function renderPreview(){
    const title = $form.find('[name=title]').val();
    const html  = $form.find('[name=bodyMd]').val();
    $title.text(title || 'Notice');
    $body.html(html || '<em style="color:#94a3b8">본문 미리보기</em>');
  }

  // --- 이벤트
  $('#btn-refresh').on('click', loadList);
  $('#btn-new').on('click', ()=>{ fillForm(null); openModal(); });
  $year.on('change', loadList); $active.on('change', loadList); $pinned.on('change', loadList);

  $tblBody.on('click', '.js-edit', function(){
    const id = $(this).closest('tr').data('id');
    // 행 데이터 재구축(간단히 테이블에서 읽거나 전체 목록 재조회가 이상적이나 여기선 미리보기 위해 다시 불러오지 않고 서버에 의존)
    fetch(`${API.update(id)}`, { headers:{'Accept':'application/json'} })
      .then(r=>r.json())
      .then(n=>{ fillForm(n); openModal(); })
      .catch(()=>alert('상세 조회 실패'));
  });

  $tblBody.on('click', '.js-preview', function(){
    const id = $(this).closest('tr').data('id');
    // 간단 프리뷰: 편집 모달 열되 저장버튼 비활성? 대신 그냥 상세조회 후 미리보기만 보여줘도 됨
    fetch(`${API.update(id)}`, { headers:{'Accept':'application/json'} })
      .then(r=>r.json())
      .then(n=>{ fillForm(n); openModal(); })
      .catch(()=>alert('상세 조회 실패'));
  });

  $tblBody.on('click', '.js-del', async function(){
    const id = $(this).closest('tr').data('id');
    if(!confirm('삭제하시겠습니까?')) return;
    showSpin();
    try{
      const res = await fetch(API.remove(id), { method:'DELETE', headers:{'X-Requested-With':'XMLHttpRequest'} });
      if(res.ok){ await loadList(); } else { alert('삭제 실패'); }
    } finally { hideSpin(); }
  });

  // 모달 닫기
  $modal.on('click', e => { if(e.target.dataset.close) closeModal(); });
  $modal.find('[data-close]').on('click', closeModal);

  // 본문 미리보기
  $('#bodyMd, [name=title]').on('input', renderPreview);

  // 템플릿 삽입
  $('#btn-insert-template').on('click', () => {
    const t = `<ul class="cols">
  <li><span>본인확인 및 비번설정</span><em>11/20(수) ~ 11/22(금)</em></li>
  <li><span>본인정보 수정기간</span><em>11/25(월) ~ 11/27(수)</em></li>
  <li><span>직원근무평가기간</span><em>12/2(월) ~ 12/6(금)</em></li>
  <li><span>직원근무평가 대상자</span><em>2024/10/31 이전 입사자 전원</em></li>
  <li><span>직원근무평가 제외</span><em>2024/11/1 이후 입사자</em></li>
  <li><span></span><em>11월 퇴사예정자</em></li>
  <li><span></span><em>출산·육아 등 휴직자</em></li>
</ul>`;
    const $ta = $('#bodyMd'); $ta.val(t); renderPreview();
  });

  // 저장
  $form.on('submit', async (e) => {
    e.preventDefault();
    const data = serializeForm();
    showSpin();
    try{
      const isUpdate = !!data.id;
      const url = isUpdate ? API.update(data.id) : API.create;
      const method = isUpdate ? 'PUT' : 'POST';
      const res = await fetch(url, {
        method, headers:{ 'Content-Type':'application/json', 'X-Requested-With':'XMLHttpRequest' },
        body: JSON.stringify(data)
      });
      if(res.ok){
        closeModal();
        await loadList();
      }else{
        alert('저장 실패');
      }
    } finally { hideSpin(); }
  });

  // 모달에서 삭제
  $('#btn-delete').on('click', async () => {
    const id = $form.find('[name=id]').val();
    if(!id) return;
    if(!confirm('삭제하시겠습니까?')) return;
    showSpin();
    try{
      const res = await fetch(API.remove(id), { method:'DELETE', headers:{'X-Requested-With':'XMLHttpRequest'} });
      if(res.ok){ closeModal(); await loadList(); } else { alert('삭제 실패'); }
    } finally { hideSpin(); }
  });

  // 초기 로드
  loadList();
})();