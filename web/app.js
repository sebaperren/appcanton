// State Global
let currentTab = 1;
let currencyMode = 'ARS'; // 'ARS' or 'USD'
let weightModeEnabled = false;

// Settings Parameters
let stateSettings = {
  tc: 1350.0,
  flete: 3400.0,
  seguroPct: 1.2,
  arancelPct: 20.0,
  tasaEstadPct: 3.0,
  ivaPct: 21.0,
  ivaAdicPct: 20.0,
  gananciasPct: 6.0,
  iibbPct: 2.5,
  despachantePct: 8.0,
  containerMaxWeightKg: 26000.0,
  incFlete: true,
  incSeguro: true,
  incArancel: true,
  incTasaEstad: true,
  incIVA: true,
  incIVAAdic: true,
  incGanancias: true,
  incIIBB: true,
  incDespachante: true
};

// Saved Articles Sample
let savedArticles = [
  { code: 'ART-CN-101', name: 'Cerámico Foshan 60x60 Pulido', supplier: 'Foshan Ceramics Co.', fob: 4.50, moq: 2000, port: 'Foshan', weightKg: 3.2 },
  { code: 'ART-CN-204', name: 'Grifería Monocomando Bronce', supplier: 'Zhejiang Plumbing Ltd.', fob: 12.80, moq: 800, port: 'Ningbo', weightKg: 1.1 }
];

let expandedCards = new Set();
let selectedCantonItem = null;

// IndexedDB Storage setup for Photo Backup
let db;
const request = indexedDB.open('CantonAppDB', 1);
request.onupgradeneeded = (e) => {
  db = e.target.result;
  if (!db.objectStoreNames.contains('photos')) {
    db.createObjectStore('photos', { keyPath: 'id', autoIncrement: true });
  }
};
request.onsuccess = (e) => { db = e.target.result; };

// Initialize PWA Service Worker
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('./sw.js').catch(err => console.log('SW reg error', err));
}

document.addEventListener('DOMContentLoaded', () => {
  setupEventListeners();
  calculateTab1();
  renderTab2List();
});

function setupEventListeners() {
  document.getElementById('btnCurrARS').addEventListener('click', () => setCurrency('ARS'));
  document.getElementById('btnCurrUSD').addEventListener('click', () => setCurrency('USD'));
  document.getElementById('btnOpenSettings').addEventListener('click', openSettingsModal);

  ['t1Fob', 't1Qty', 't1Weight'].forEach(id => {
    document.getElementById(id).addEventListener('input', calculateTab1);
  });
}

function setCurrency(mode) {
  currencyMode = mode;
  document.getElementById('btnCurrARS').classList.toggle('active', mode === 'ARS');
  document.getElementById('btnCurrUSD').classList.toggle('active', mode === 'USD');
  calculateTab1();
  renderTab2List();
  updateTab3Comparison();
}

function switchTab(tabNum) {
  currentTab = tabNum;
  [1, 2, 3].forEach(n => {
    document.getElementById(`tabBtn${n}`).classList.toggle('active', n === tabNum);
    document.getElementById(`tab${n}Content`).style.display = (n === tabNum) ? 'block' : 'none';
  });
  if (tabNum === 3) updateTab3Comparison();
}

function toggleWeightMode() {
  weightModeEnabled = !weightModeEnabled;
  const btn = document.getElementById('btnToggleWeightProrate');
  btn.textContent = weightModeEnabled 
    ? '⚖️ Prorratear Flete por Peso (ACTIVADO)' 
    : '⚖️ Prorratear Flete por Peso (DESACTIVADO)';
  btn.classList.toggle('active', weightModeEnabled);
  calculateTab1();
  renderTab2List();
}

function calculateTab1() {
  const fob = parseFloat(document.getElementById('t1Fob').value) || 10.0;
  const qty = parseInt(document.getElementById('t1Qty').value) || 1000;
  const weight = parseFloat(document.getElementById('t1Weight').value) || 0.0;

  const html = generateBreakdownTableHTML(fob, qty, weight, '(TAB 1 MANUAL)');
  document.getElementById('t1BreakdownTable').innerHTML = html;
}

function generateBreakdownTableHTML(fobUnit, containerQty, itemWeightKg, titleSuffix) {
  const s = stateSettings;
  const qty = containerQty > 0 ? containerQty : 1;
  const fobTotal = fobUnit * qty;

  const baseFlete = s.incFlete ? s.flete : 0.0;
  let effectiveFlete = baseFlete;
  if (s.incFlete && weightModeEnabled && itemWeightKg > 0) {
    const totalLotWeight = itemWeightKg * qty;
    const ratio = Math.min(1.0, totalLotWeight / s.containerMaxWeightKg);
    effectiveFlete = baseFlete * ratio;
  }

  const seguroTotal = fobTotal * ((s.incSeguro ? s.seguroPct : 0) / 100.0);
  const cifTotal = fobTotal + effectiveFlete + seguroTotal;
  const derechosTotal = cifTotal * ((s.incArancel ? s.arancelPct : 0) / 100.0);
  const tasaEstadTotal = cifTotal * ((s.incTasaEstad ? s.tasaEstadPct : 0) / 100.0);
  const baseImponible = cifTotal + derechosTotal + tasaEstadTotal;
  const ivaTotal = baseImponible * ((s.incIVA ? s.ivaPct : 0) / 100.0);
  const ivaAdicTotal = baseImponible * ((s.incIVAAdic ? s.ivaAdicPct : 0) / 100.0);
  const gananciasTotal = baseImponible * ((s.incGanancias ? s.gananciasPct : 0) / 100.0);
  const iibbTotal = baseImponible * ((s.incIIBB ? s.iibbPct : 0) / 100.0);
  const gastosDespachante = baseImponible * ((s.incDespachante ? s.despachantePct : 0) / 100.0);

  const costoRealIncididoUSD = baseImponible + iibbTotal + gastosDespachante;
  const unitCostoIncididoUSD = costoRealIncididoUSD / qty;

  const totalLandedUSD = baseImponible + ivaTotal + ivaAdicTotal + gananciasTotal + iibbTotal + gastosDespachante;
  const unitLandedUSD = totalLandedUSD / qty;

  const mult = (currencyMode === 'ARS') ? s.tc : 1.0;
  const curr = (currencyMode === 'ARS') ? 'ARS' : 'USD';

  const fmt = (valUSD) => {
    const v = valUSD * mult;
    return (currencyMode === 'ARS')
      ? `$ ${v.toLocaleString('es-AR', {minimumFractionDigits:2, maximumFractionDigits:2})}`
      : `$ ${v.toFixed(2)}`;
  };

  const rows = [
    { label: 'Valor FOB', pct: '', unit: fobUnit, total: fobTotal },
    { label: weightModeEnabled ? 'Flete (Prorr. Peso)' : 'Flete Marítimo', pct: s.incFlete ? '' : 'OFF', unit: effectiveFlete / qty, total: effectiveFlete },
    { label: 'Seguro', pct: s.incSeguro ? `${s.seguroPct}%` : 'OFF', unit: seguroTotal / qty, total: seguroTotal },
    { label: 'CIF', pct: '', unit: cifTotal / qty, total: cifTotal, header: true },
    { label: 'Derecho Impo', pct: s.incArancel ? `${s.arancelPct}%` : 'OFF', unit: derechosTotal / qty, total: derechosTotal },
    { label: 'Tasa Estadística', pct: s.incTasaEstad ? `${s.tasaEstadPct}%` : 'OFF', unit: tasaEstadTotal / qty, total: tasaEstadTotal },
    { label: 'Base Imponible', pct: '', unit: baseImponible / qty, total: baseImponible, header: true },
    { label: 'IVA', pct: s.incIVA ? `${s.ivaPct}%` : 'OFF', unit: ivaTotal / qty, total: ivaTotal },
    { label: 'IVA Adicional', pct: s.incIVAAdic ? `${s.ivaAdicPct}%` : 'OFF', unit: ivaAdicTotal / qty, total: ivaAdicTotal },
    { label: 'Ganancias', pct: s.incGanancias ? `${s.gananciasPct}%` : 'OFF', unit: gananciasTotal / qty, total: gananciasTotal },
    { label: 'II.BB', pct: s.incIIBB ? `${s.iibbPct}%` : 'OFF', unit: iibbTotal / qty, total: iibbTotal },
    { label: 'Gastos Desp/Forw', pct: s.incDespachante ? `${s.despachantePct}%` : 'OFF', unit: gastosDespachante / qty, total: gastosDespachante },
    { label: 'COSTO INCIDIDO NETO', pct: '', unit: unitCostoIncididoUSD, total: costoRealIncididoUSD, costNet: true },
    { label: 'TOTAL DESEMBOLSO LOTE', pct: '', unit: unitLandedUSD, total: totalLandedUSD, totalOutlay: true }
  ];

  let rowsHTML = '';
  rows.forEach(r => {
    let cls = '';
    if (r.totalOutlay) cls = 'row-total-outlay';
    else if (r.costNet) cls = 'row-cost-net';
    else if (r.header) cls = 'row-header';

    rowsHTML += `
      <tr class="${cls}">
        <td>${r.label}</td>
        <td style="text-align:center;">${r.pct}</td>
        <td>${fmt(r.unit)}</td>
        <td>${fmt(r.total)}</td>
      </tr>
    `;
  });

  return `
    <div class="card" style="padding: 10px;">
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <strong style="font-size:11px; color:var(--primary-color);">📋 DESGLOSE LANDED ${titleSuffix}</strong>
        <span style="font-size:10px; font-weight:bold; color:var(--accent-color); background:#E8F5E9; padding:2px 6px; border-radius:4px;">Lote: ${qty} u</span>
      </div>
      <div class="table-container">
        <table class="breakdown-table">
          <thead>
            <tr>
              <th>CONCEPTO</th>
              <th style="text-align:center;">%</th>
              <th>UNITARIO (${curr})</th>
              <th>TOTAL LOTE (${curr})</th>
            </tr>
          </thead>
          <tbody>
            ${rowsHTML}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

function renderTab2List() {
  const query = (document.getElementById('tab2Search').value || '').toLowerCase();
  const listEl = document.getElementById('tab2List');
  document.getElementById('savedCount').textContent = savedArticles.length;

  const filtered = savedArticles.filter(a => 
    a.name.toLowerCase().includes(query) || a.code.toLowerCase().includes(query) || a.supplier.toLowerCase().includes(query)
  );

  if (filtered.length === 0) {
    listEl.innerHTML = `<div class="card" style="text-align:center; padding:16px; color:#666;">No hay artículos guardados</div>`;
    return;
  }

  let html = '';
  filtered.forEach(art => {
    const isExpanded = expandedCards.has(art.code);
    const fobVal = (currencyMode === 'ARS') 
      ? `$ ${(art.fob * stateSettings.tc).toLocaleString('es-AR', {minimumFractionDigits:2})} ARS`
      : `$ ${art.fob.toFixed(2)} USD`;

    html += `
      <div class="card" style="border: 1px solid ${isExpanded ? 'var(--secondary-color)' : '#E0E0E0'};">
        <div style="display:flex; justify-content:space-between; align-items:flex-start; cursor:pointer;" onclick="toggleCardExpand('${art.code}')">
          <div>
            <strong style="font-size:12px; color:var(--primary-color);">${art.name}</strong>
            <div style="font-size:10px; color:var(--text-muted);">Proveedor: ${art.supplier} | Código: ${art.code}</div>
          </div>
          <span class="fob-tag">${fobVal}</span>
        </div>

        <div style="display:flex; justify-content:space-between; align-items:center; margin-top:6px; font-size:10px; color:#444;">
          <span>Puerto: ${art.port} | MOQ: ${art.moq} u</span>
          <span style="color:var(--secondary-color); font-weight:bold;" onclick="toggleCardExpand('${art.code}')">
            ${isExpanded ? '🔼 Ocultar Desglose' : '🔽 Ver Desglose Landed'}
          </span>
        </div>

        ${isExpanded ? `
          <hr style="margin:8px 0; border:none; border-top:1px solid #EEE;">
          ${generateBreakdownTableHTML(art.fob, art.moq, art.weightKg, `(${art.supplier})`)}
        ` : ''}

        <button class="btn-success" onclick="selectForTab3('${art.code}')" style="margin-top:8px;">
          📊 COMPARAR ESTE ARTÍCULO EN TAB 3
        </button>
      </div>
    `;
  });

  listEl.innerHTML = html;
}

function toggleCardExpand(code) {
  if (expandedCards.has(code)) expandedCards.delete(code);
  else expandedCards.add(code);
  renderTab2List();
}

function selectForTab3(code) {
  selectedCantonItem = savedArticles.find(a => a.code === code);
  switchTab(3);
}

function sendTab1ToTab3() {
  selectedCantonItem = {
    code: 'MANUAL',
    name: document.getElementById('t1Name').value,
    fob: parseFloat(document.getElementById('t1Fob').value) || 10.0,
    moq: parseInt(document.getElementById('t1Qty').value) || 1000,
    port: document.getElementById('t1Port').value,
    weightKg: parseFloat(document.getElementById('t1Weight').value) || 0.0
  };
  switchTab(3);
}

function updateTab3Comparison() {
  const item = selectedCantonItem || {
    name: document.getElementById('t1Name').value,
    fob: parseFloat(document.getElementById('t1Fob').value) || 10.0,
    moq: parseInt(document.getElementById('t1Qty').value) || 1000,
    weightKg: parseFloat(document.getElementById('t1Weight').value) || 0.0
  };

  const perrenARS = 14175.0; // Costo Perren Sin IVA
  const perrenUSD = perrenARS / stateSettings.tc;

  // Landed calculation for Canton item
  const s = stateSettings;
  const fobTotal = item.fob * item.moq;
  const flete = s.incFlete ? s.flete : 0;
  const seguro = fobTotal * (s.incSeguro ? s.seguroPct / 100 : 0);
  const cif = fobTotal + flete + seguro;
  const derechos = cif * (s.incArancel ? s.arancelPct / 100 : 0);
  const tasaEstad = cif * (s.incTasaEstad ? s.tasaEstadPct / 100 : 0);
  const base = cif + derechos + tasaEstad;
  const iibb = base * (s.incIIBB ? s.iibbPct / 100 : 0);
  const desp = base * (s.incDespachante ? s.despachantePct / 100 : 0);

  const costoIncididoTotalUSD = base + iibb + desp;
  const unitCostoIncididoUSD = costoIncididoTotalUSD / item.moq;
  const unitCostoIncididoARS = unitCostoIncididoUSD * s.tc;

  const ahorroUSD = perrenUSD - unitCostoIncididoUSD;
  const ahorroARS = perrenARS - unitCostoIncididoARS;
  const ahorroPct = (ahorroUSD / perrenUSD) * 100.0;

  document.getElementById('cantonProductDetail').innerHTML = `
    <strong>${item.name}</strong><br>
    <span style="color:var(--text-muted);">Costo Puesto Incidido Landed:</span> 
    <strong>$ ${unitCostoIncididoARS.toLocaleString('es-AR', {minimumFractionDigits:2})} ARS ($ ${unitCostoIncididoUSD.toFixed(2)} USD)</strong>
  `;

  document.getElementById('savingValTxt').textContent = `$ ${ahorroARS.toLocaleString('es-AR', {minimumFractionDigits:2})} ARS / u ($ ${ahorroUSD.toFixed(2)} USD)`;
  document.getElementById('savingPctTxt').textContent = `Reducción de costo del ${ahorroPct.toFixed(1)}%`;
}

// Modal Settings
function openSettingsModal() { document.getElementById('modalSettings').classList.add('open'); }
function closeSettingsModal() { 
  stateSettings.tc = parseFloat(document.getElementById('sTC').value) || 1350;
  stateSettings.flete = parseFloat(document.getElementById('sFlete').value) || 3400;
  stateSettings.arancelPct = parseFloat(document.getElementById('sArancel').value) || 20;
  stateSettings.tasaEstadPct = parseFloat(document.getElementById('sTasaEstad').value) || 3;
  stateSettings.ivaPct = parseFloat(document.getElementById('sIVA').value) || 21;
  stateSettings.ivaAdicPct = parseFloat(document.getElementById('sIVAAdic').value) || 20;
  stateSettings.gananciasPct = parseFloat(document.getElementById('sGanancias').value) || 6;
  stateSettings.iibbPct = parseFloat(document.getElementById('sIIBB').value) || 2.5;

  document.getElementById('lblDolarTC').textContent = `Dólar TC: $ ${stateSettings.tc.toLocaleString('es-AR', {minimumFractionDigits:2})} ARS`;
  document.getElementById('modalSettings').classList.remove('open'); 
  calculateTab1();
  renderTab2List();
  updateTab3Comparison();
}

function toggleInc(key) {
  stateSettings[key] = !stateSettings[key];
  document.getElementById(`chip_${key}`).classList.toggle('selected', stateSettings[key]);
}

// Camera OCR & Photo Storage
function openOcrCamera() { document.getElementById('modalOcr').classList.add('open'); }
function closeOcrCamera() { document.getElementById('modalOcr').classList.remove('open'); }

function processOcrImage(event) {
  const file = event.target.files[0];
  if (!file) return;

  const statusEl = document.getElementById('ocrStatus');
  const outEl = document.getElementById('ocrOutput');
  statusEl.textContent = '⏳ Escaneando texto y precios con OCR...';
  outEl.textContent = '';

  // Save photo locally in IndexedDB as backup
  const reader = new FileReader();
  reader.onload = (e) => {
    if (db) {
      const tx = db.transaction('photos', 'readwrite');
      tx.objectStore('photos').add({ image: e.target.result, date: new Date().toISOString() });
    }
  };
  reader.readAsDataURL(file);

  Tesseract.recognize(file, 'eng+spa', { logger: m => console.log(m) })
    .then(({ data: { text } }) => {
      statusEl.textContent = '✅ Reconocimiento OCR Completado!';
      outEl.textContent = text;

      // Extract numeric values if present
      const numbers = text.match(/\d+(\.\d+)?/g);
      if (numbers && numbers.length > 0) {
        document.getElementById('t1Fob').value = numbers[0];
        calculateTab1();
      }
    })
    .catch(err => {
      statusEl.textContent = '⚠️ Error en OCR';
      console.error(err);
    });
}
