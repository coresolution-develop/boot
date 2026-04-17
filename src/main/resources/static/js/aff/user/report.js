document.addEventListener('DOMContentLoaded', () => {
  // ---------- util ----------
  const $  = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
  const toNum = (v, def = 0) => (Number.isFinite(+v) ? +v : def);

  Chart.defaults.font.family = "'Noto Sans KR','Apple SD Gothic Neo','Malgun Gothic',sans-serif";

  const overlay = document.getElementById('spinner-overlay');
  const topbar  = document.getElementById('topbar-progress');
  const FLAG    = '__nav_in_progress__';

  // ---------- payload ----------
  const payloadEl = $('#chart-data');
  const payload   = payloadEl ? JSON.parse(payloadEl.textContent || '{}') : {};
  const labels    = payload.labels || [];

  const showSpinner = () => {
    overlay?.classList.add('show');
    if (topbar){
      topbar.classList.add('active');
      topbar.style.width = '10%';
    }
  };

  const finishTopbar = () => {
    if (topbar){
      topbar.classList.add('done');
      topbar.style.width = '100%';
    }
  };

  const hideSpinnerAndTopbar = () => {
    overlay?.classList.remove('show');
    if (topbar){
      topbar.classList.add('no-anim');
      topbar.classList.remove('active','done');
      topbar.style.width = '0%';
      requestAnimationFrame(() => topbar.classList.remove('no-anim'));
    }
  };

  // 뒤로 돌아온 직후(리다이렉트 후) 초기화
  if (sessionStorage.getItem(FLAG) === '1'){
    sessionStorage.removeItem(FLAG);
    hideSpinnerAndTopbar();

    // ⏱️ 실제 처리시간 측정 후 저장(다음 제출의 추정치로 사용)
    const ts = sessionStorage.getItem('__report_submit_ts__');
    if (ts) {
      const rtt = Math.max(0, Date.now() - (+ts));
      // 5s ~ 15s 사이로 클램프
      const est = Math.min(15000, Math.max(5000, rtt));
      sessionStorage.setItem('__report_last_rtt__', String(est));
      sessionStorage.removeItem('__report_submit_ts__');
    }
  }

  // ✅ 폼 제출 시 스피너/탑바 작동
  document.addEventListener('submit', (e) => {
    const form = e.target?.closest('form[data-spinner="on"]');
    if (!form) return;

    if (form.dataset.busy === '1') return;
    form.dataset.busy = '1';

    showSpinner();

    const btn = form.querySelector('.js-async-submit, .orange-btn, .search-btn, button[type="submit"]');
    btn?.classList.add('is-loading');

    // 복귀 후 초기화용 플래그 + 이번 제출 시작 시각 저장
    sessionStorage.setItem('__nav_in_progress__', '1');
    sessionStorage.setItem('__report_submit_ts__', String(Date.now()));

    // --- ⏱️ 진행바 애니메이션: "지난 rtt"를 예상치로 사용 ---
    const lastRtt = parseInt(sessionStorage.getItem('__report_last_rtt__') || '0', 10);
    // 기본값 10s, 5~15s로 클램프
    const estMs = lastRtt > 0 ? Math.min(15000, Math.max(5000, lastRtt)) : 10000;

    const start = performance.now();
    let rafId;
    const easeOut = t => 1 - Math.pow(1 - t, 3); // 감속 이징

    const tick = (now) => {
      const elapsed = now - start;

      if (elapsed <= estMs) {
        // 10% → 90% (예상치까지)
        const p = easeOut(elapsed / estMs);
        const w = 10 + (90 - 10) * p;
        if (topbar) topbar.style.width = w + '%';
      } else {
        // 예상치 초과 시 아주 느린 크리핑(최대 96%)
        const extra = (elapsed - estMs) / 1000;
        const creep = Math.min(6, extra * 0.2); // 초당 0.2%
        if (topbar) topbar.style.width = (90 + creep) + '%';
      }
      rafId = requestAnimationFrame(tick);
    };
    rafId = requestAnimationFrame(tick);

    // 네비게이션 직전 마무리
    window.addEventListener('pagehide', () => {
      cancelAnimationFrame(rafId);
      finishTopbar();
    }, { once: true });
  });

  // ---------- 토스트 ----------
  (function setupToastOnReturn(){
    const toast = document.getElementById('toast');
    if (!toast) return;

    const url = new URL(window.location.href);
    const shouldShow = url.searchParams.get('show') === '1';

    if (shouldShow) {
      if (!toast.textContent || !toast.textContent.trim()) {
        toast.textContent = '요약을 갱신했습니다.';
      }
      toast.classList.add('show');
      setTimeout(() => {
        toast.classList.remove('show');
      }, 2400);

      url.searchParams.delete('show');
      history.replaceState({}, '', url);
    }
  })();

  // ---------- 보고서 페이드인 ----------
  const wrapper = $('#report-wrapper');
  if (wrapper && (wrapper.dataset.visible === 'true' || !wrapper.hasAttribute('data-visible'))) {
    requestAnimationFrame(() => wrapper.classList.add('reveal-in'));
  }

  // ---------- 게이지 ----------
  function setBar(barId, markerId, myValue100, avgValue100) {
    const bar    = document.getElementById(barId);
    const marker = document.getElementById(markerId);
    if (!bar || !marker) return;

    const fillEl = bar.querySelector('.fill');
    const avgPin = bar.querySelector('.avg-pin, #pin-avg-ev');

    const my  = Math.max(0, Math.min(100, +myValue100  || 0)); // 내 점수
    const avg = Math.max(0, Math.min(100, +avgValue100 || 0)); // 평균

    // 1) 게이지 채움 = 내 점수
    if (fillEl) {
      fillEl.style.width = my + '%';
      fillEl.setAttribute('data-label', String(Math.round(my)));
    }

    // 2) 평균 위치 핀
    if (avgPin) {
      avgPin.style.left = avg + '%';
      avgPin.style.transform = 'translateX(-50%)';
    }

    // 3) 마커 텍스트
    marker.textContent = `평균 ${Math.round(avg)}점`;
    marker.style.left = avg + '%';
    marker.style.transform = 'translateX(-50%)';
    marker.classList.toggle('is-edge-left',  avg <= 2);
    marker.classList.toggle('is-edge-right', avg >= 98);

    // 툴팁용 값 저장
    bar.dataset.my   = String(Math.round(my));
    marker.dataset.avg = String(Math.round(avg));
  }

  const applyGauge = () => {
    const my  = +(payload.myOverall100 ?? 0);
    const avg = +(payload.cohortOverall100 ?? payload.evOverall100 ?? 0);
    setBar('bar-ev', 'marker-me-ev', my, avg);
  };
  applyGauge();
  window.addEventListener('load', applyGauge);
  window.addEventListener('resize', applyGauge);

  // ---------- 툴팁 ----------
  const tooltipEl = (() => {
    const el = document.createElement('div');
    el.className = 'tooltip';
    el.style.display = 'none';
    document.body.appendChild(el);
    return el;
  })();

  const attachTooltip = (target, getText) => {
    if (!target) return;
    const show = e => {
      const t = getText();
      if (!t) return;
      tooltipEl.textContent = t;
      tooltipEl.style.display = 'block';
      move(e);
    };
    const move = e => {
      tooltipEl.style.left = e.clientX + 'px';
      tooltipEl.style.top  = (e.clientY - 10) + 'px';
    };
    const hide = () => {
      tooltipEl.style.display = 'none';
    };
    target.addEventListener('mouseenter', show);
    target.addEventListener('mousemove',  move);
    target.addEventListener('mouseleave', hide);
  };

  const barEl    = document.getElementById('bar-ev');
  const markerEl = document.getElementById('marker-me-ev');
  const avgPinEl = document.getElementById('pin-avg-ev');

  attachTooltip(barEl,    () => `내 점수 ${barEl?.dataset.my ?? 0}점`);
  attachTooltip(avgPinEl, () => `평가구간 평균 ${barEl?.dataset.avg ?? 0}점`);
  attachTooltip(markerEl, () => `평가구간 평균 ${markerEl?.dataset.avg ?? 0}점`);

  window.addEventListener('beforeprint', applyGauge);

  // ---------- 레이더 ----------
  if (window.__radarChart) window.__radarChart.destroy();
  const radarEl = document.getElementById('radar');
  if (radarEl && labels.length) {
    const me = labels.map(l => payload.myArea100?.[l] ?? 0);
    const ev = labels.map(l => payload.evArea100?.[l] ?? 0);

    window.__radarChart = new Chart(radarEl, {
      type: 'radar',
      data: {
        labels,
        datasets: [
          {
            label: '평가구간별 평균',
            data: ev,
            fill: true,
            backgroundColor: 'rgba(255,255,255,0)',
            borderColor: 'rgba(99,127,255,1)',
            borderWidth: 1,
            pointRadius: 2,
            order: 1
          },
          {
            label: '내점수',
            data: me,
            fill: true,
            backgroundColor: 'rgba(255,255,255,0)',
            borderColor: 'rgba(255,99,132,1)',
            borderWidth: 2,
            pointRadius: 2.5,
            order: 2
          }
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        aspectRatio: 1,
        scales: {
          r: {
            min: 50,
            max: 100,
            beginAtZero: false,
            ticks: {
              stepSize: 10,
              backdropColor: 'transparent'
            },
            grid: {
              circular: false
            }
          }
        },
        plugins: {
          legend: { display: false },
          filler: { propagate: false }
        },
      },
    });
  }

  // ---------- 영역별 응답 분포 ----------
  const distCanvas = $('#distChart');
  if (distCanvas) {
    if (distCanvas._chartInstance) distCanvas._chartInstance.destroy();

    const distMap = payload.myAreaDist || {};
    const CATS   = ['매우미흡','미흡','보통','우수','매우우수'];
    const COLORS = {
      '매우미흡':'#f8bfbf',
      '미흡':'#fbd7b4',
      '보통':'#d9d9d9',
      '우수':'#bcd2ff',
      '매우우수':'#f8bdd8'
    };

    const totals = labels.map(a => CATS.reduce((s,c) => s + (distMap[a]?.[c] || 0), 0));

    const datasets = CATS.map(cat => ({
      label: cat,
      data: labels.map((a, i) => {
        const t = totals[i] || 1;
        return ((distMap[a]?.[cat] || 0) / t) * 100;
      }),
      backgroundColor: COLORS[cat],
      borderWidth: 0,
      stack: 'percent'
    }));

    const chart = new Chart(distCanvas.getContext('2d'), {
      type: 'bar',
      data: { labels, datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        indexAxis: 'y',
        devicePixelRatio: 2,
        datasets: {
          bar: {
            barThickness: 12,
            maxBarThickness: 12
          }
        },
        scales: {
          x: {
            stacked: true,
            min: 0,
            max: 100,
            ticks: {
              callback: v => v + '%'
            },
            grid: { drawBorder: false }
          },
          y: {
            stacked: true,
            grid: { drawBorder: false }
          }
        },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => {
                const area = ctx.label;
                const cat  = ctx.dataset.label;
                const cnt  = (distMap[area]?.[cat] ?? 0);
                const pct  = ctx.parsed.x ?? 0;
                return `${cat}: ${cnt}건 (${pct.toFixed(0)}%)`;
              },
              title: (items) => {
                if (!items?.length) return '';
                const area = items[0].label;
                const i    = items[0].dataIndex;
                return `${area} · 총 ${totals[i] || 0}건`;
              }
            }
          }
        }
      }
    });
    distCanvas._chartInstance = chart;

    // 부모 크기 변화에 맞춰 재계산
    const distWrapper = distCanvas ? distCanvas.closest('.chart-fixed') : null;
    if (window.ResizeObserver && distWrapper) {
      new ResizeObserver(() => {
        if (distCanvas._chartInstance) distCanvas._chartInstance.resize();
      }).observe(distWrapper);
    }

    // 범례 생성
    const legendRoot = document.getElementById('distLegend');
    if (legendRoot) {
      legendRoot.innerHTML = '';
      CATS.forEach(cat => {
        const item = document.createElement('div');
        item.className = 'legend-item';
        const sw = document.createElement('span');
        sw.className = 'legend-swatch';
        sw.style.background = COLORS[cat];
        const lb = document.createElement('span');
        lb.textContent = cat;
        item.append(sw, lb);
        legendRoot.appendChild(item);
      });
    }
  }

  // ---------- 인쇄 훅 ----------
  function beforePrint() {
    const cohort = +(payload.cohortOverall100 ?? payload.evOverall100 ?? 0);
    setBar('bar-ev', 'marker-me-ev', payload.myOverall100, cohort);

    if (window.__radarChart) {
      window.__radarChart.options.animation = false;
    window.__radarChart.resize();   // ← 추가
      window.__radarChart.update(0);
    }
    if (distCanvas && distCanvas._chartInstance) {
      distCanvas._chartInstance.options.animation = false;
    ch.resize();                    // ← 추가
      distCanvas._chartInstance.update(0);
    }
  }

  function afterPrint() {
    if (window.__radarChart) {
      window.__radarChart.options.responsive = true;
      window.__radarChart.update();
    }
    if (distCanvas && distCanvas._chartInstance) {
      distCanvas._chartInstance.options.responsive = true;
      distCanvas._chartInstance.update();
    }
  }

  window.addEventListener('beforeprint', beforePrint);
  window.addEventListener('afterprint', afterPrint);
});