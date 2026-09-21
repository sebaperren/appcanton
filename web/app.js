// State Global
let currentSection = 'inicio';
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

// Base de Datos Perren Flexxus BI (Muestra Integrada de 12.074 artículos)
let perrenSqlDatabase = [
  { sku: 'PERREN-1042', description: 'Azulejo cerámico 20x20 Blanco Satinado', brand: 'Cortines', category: 'Revestimientos', costNoVAT: 14175.0, priceVAT: 21500.0, abcClass: 'A' },
  { sku: 'PERREN-2088', description: 'Porcelanato 60x60 Gris Pulido San Lorenzo', brand: 'San Lorenzo', category: 'Porcelanatos', costNoVAT: 28500.0, priceVAT: 42000.0, abcClass: 'A' },
  { sku: 'PERREN-3015', description: 'Grifería Monocomando FV Cocina Bronce', brand: 'FV', category: 'Grifería', costNoVAT: 48900.0, priceVAT: 74000.0, abcClass: 'B' },
  { sku: 'PERREN-4090', description: 'Inodoro Largo Ferrum Bari Blanco', brand: 'Ferrum', category: 'Sanitarios', costNoVAT: 89000.0, priceVAT: 135000.0, abcClass: 'A' },
  { sku: 'PERREN-5012', description: 'Bidet 3 Orificios Ferrum Bari Blanco', brand: 'Ferrum', category: 'Sanitarios', costNoVAT: 65000.0, priceVAT: 98000.0, abcClass: 'B' },
  { sku: 'PERREN-6045', description: 'Pegamento Weber Keraflor 30kg', brand: 'Weber', category: 'Adhesivos', costNoVAT: 8500.0, priceVAT: 12900.0, abcClass: 'C' }
];

// Proveedores Guardados y Cotizaciones Chinas
let localSuppliers = [
  {
    id: 'SUP-01',
    companyName: 'Foshan Ceramics Co. Ltd',
    stand: 'Hall 9.2 - Stand C14',
    category: 'Cerámicos y Azulejos',
    contactName: 'Jacky Zhang',
    phone: '+86 138 0000 1111',
    email: 'jacky@foshanceramics.cn',
    articles: [
      { code: 'ART-CN-101', name: 'Cerámico Foshan 60x60 Pulido', fob: 4.50, moq: 2000, port: 'Foshan', weightKg: 3.2, supplier: 'Foshan Ceramics Co. Ltd' }
    ]
  },
  {
    id: 'SUP-02',
    companyName: 'Zhejiang Plumbing Ltd',
    stand: 'Hall 11.1 - Stand E05',
    category: 'Grifería y Sanitarios',
    contactName: 'Emily Chen',
    phone: '+86 139 2222 3333',
    email: 'emily@zhengjiangplumbing.cn',
    articles: [
      { code: 'ART-CN-204', name: 'Grifería Monocomando Bronce', fob: 12.80, moq: 800, port: 'Ningbo', weightKg: 1.1, supplier: 'Zhejiang Plumbing Ltd' }
    ]
  }
];

let expandedCards = new Set();
let selectedPerrenProduct = perrenSqlDatabase[0];
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
  updateMetrics();
  populateSupplierDropdown();
  calculateTab1();
  renderTab2List();
  renderTarjeteroList();
  renderPerrenSearchResults();
});

function setupEventListeners() {
  document.getElementById('btnCurrARS').addEventListener('click', () => setCurrency('ARS'));
  document.getElementById('btnCurrUSD').addEventListener('click', () => setCurrency('USD'));
  document.getElementById('btnOpenSettings').addEventListener('click', openSettingsModal);

  ['t1Fob', 't1Qty', 't1Weight'].forEach(id => {
    document.getElementById(id).addEventListener('input', calculateTab1);
  });
}

function switchMainSection(secName) {
  currentSection = secName;
  const sections = ['inicio', 'addProveedor', 'costos', 'comparador', 'tarjetero'];
  sections.forEach(s => {
    const el = document.getElementById(`sec${s.charAt(0).toUpperCase() + s.slice(1)}`);
    const nav = document.getElementById(`nav${s.charAt(0).toUpperCase() + s.slice(1)}`);
    if (el) el.style.display = (s === secName) ? 'block' : 'none';
    if (nav) nav.classList.toggle('active', s === secName);
  });
}

function updateMetrics() {
  const totalSuppliers = localSuppliers.length;
  let totalArticles = 0;
  localSuppliers.forEach(s => totalArticles += s.articles.length);

  document.getElementById('lblTotalSuppliers').textContent = totalSuppliers;
  document.getElementById('lblTotalArticles').textContent = totalArticles;
  document.getElementById('savedCount').textContent = totalArticles;
}

function populateSupplierDropdown() {
  const sel = document.getElementById('pArticleSupplierSelect');
  if (!sel) return;
  sel.innerHTML = '';
  localSuppliers.forEach(s => {
    const opt = document.createElement('option');
    opt.value = s.id;
    opt.textContent = `${s.companyName} (${s.stand})`;
    sel.appendChild(opt);
  });
}

function saveNewSupplier() {
  const name = document.getElementById('pCompany').value.trim();
  if (!name) {
    alert('Ingresa el nombre de la empresa proveedora.');
    return;
  }
  const newSup = {
    id: `SUP-${Date.now()}`,
    companyName: name,
    stand: document.getElementById('pStand').value || 'Sin Stand',
    category: document.getElementById('pCategory').value || 'General',
    contactName: document.getElementById('pContact').value || 'Sin Contacto',
    phone: document.getElementById('pPhone').value || '',
    email: document.getElementById('pEmail').value || '',
    articles: []
  };
  localSuppliers.push(newSup);
  updateMetrics();
  populateSupplierDropdown();
  renderTarjeteroList();
  alert(`✅ Proveedor "${name}" guardado exitosamente.`);
  document.getElementById('pCompany').value = '';
}

function saveNewArticleToSupplier() {
  const supId = document.getElementById('pArticleSupplierSelect').value;
  const sup = localSuppliers.find(s => s.id === supId);
  if (!sup) return;

  const code = document.getElementById('artCode').value || `ART-${Date.now() % 1000}`;
  const name = document.getElementById('artName').value || 'Artículo Cotizado';
  const fob = parseFloat(document.getElementById('artFob').value) || 10.0;
  const moq = parseInt(document.getElementById('artMoq').value) || 1000;
  const port = document.getElementById('artPort').value || 'Foshan';
  const weightKg = parseFloat(document.getElementById('artWeight').value) || 2.5;

  const newArt = { code, name, fob, moq, port, weightKg, supplier: sup.companyName };
  sup.articles.push(newArt);

  updateMetrics();
  renderTab2List();
  alert(`✅ Artículo "${name}" guardado en el catálogo de ${sup.companyName}.`);
  document.getElementById('artCode').value = '';
  document.getElementById('artName').value = '';
}

function renderTarjeteroList() {
  const el = document.getElementById('tarjeteroList');
  if (!el) return;
  let html = '';
  localSuppliers.forEach(sup => {
    const waClean = (sup.phone || '').replace(/[^0-9]/g, '');
    html += `
      <div class="card" style="border-left: 4px solid var(--primary-color);">
        <strong style="font-size:13px; color:var(--primary-color);">${sup.companyName}</strong>
        <div style="font-size:10px; color:var(--text-muted); margin-top:2px;">${sup.stand} | Rubro: ${sup.category}</div>
        <hr style="margin:6px 0; border:none; border-top:1px solid #EEE;">
        <div style="font-size:11px; margin-bottom:6px;">
          👤 <strong>${sup.contactName}</strong><br>
          📞 ${sup.phone || 'Sin Teléfono'}<br>
          ✉️ ${sup.email || 'Sin Email'}
        </div>
        <div style="display:flex; gap:6px;">
          ${waClean ? `<a href="https://wa.me/${waClean}" target="_blank" class="btn-whatsapp">💬 Enviar WhatsApp</a>` : ''}
          ${sup.phone ? `<a href="tel:${sup.phone}" class="btn-chip" style="text-decoration:none;">📞 Llamar</a>` : ''}
        </div>
      </div>
    `;
  });
  el.innerHTML = html;
}

function renderGlobalSearchResults() {
  const query = (document.getElementById('globalSearchInput').value || '').toLowerCase();
  const el = document.getElementById('globalSearchResults');
  if (!el) return;

  if (!query) {
    el.innerHTML = '<div style="font-size:11px; color:#666; text-align:center; padding:10px;">Ingresa un término de búsqueda...</div>';
    return;
  }

  let html = '';
  // Search in Perren SQL
  const filteredPerren = perrenSqlDatabase.filter(p => p.sku.toLowerCase().includes(query) || p.description.toLowerCase().includes(query) || p.brand.toLowerCase().includes(query));
  filteredPerren.forEach(p => {
    html += `
      <div class="card" style="border-left: 4px solid var(--secondary-color);">
        <strong style="font-size:12px; color:var(--secondary-color);">${p.sku} - ${p.description}</strong>
        <div style="font-size:10px; color:#666;">Marca: ${p.brand} | Rubro: ${p.category}</div>
        <div style="font-size:11px; font-weight:bold; color:var(--primary-color); margin-top:4px;">Costo Sin IVA: $ ${p.costNoVAT.toLocaleString('es-AR')} ARS</div>
      </div>
    `;
  });

  // Search in Canton Suppliers
  localSuppliers.forEach(sup => {
    sup.articles.forEach(art => {
      if (art.name.toLowerCase().includes(query) || art.code.toLowerCase().includes(query) || sup.companyName.toLowerCase().includes(query)) {
        html += `
          <div class="card" style="border-left: 4px solid var(--accent-color);">
            <strong style="font-size:12px; color:var(--accent-color);">${art.name} (${sup.companyName})</strong>
            <div style="font-size:10px; color:#666;">Código: ${art.code} | Puerto: ${art.port}</div>
            <div style="font-size:11px; font-weight:bold; color:var(--accent-color); margin-top:4px;">FOB: $ ${art.fob.toFixed(2)} USD</div>
          </div>
        `;
      }
    });
  });

  el.innerHTML = html || '<div style="font-size:11px; color:#666; text-align:center; padding:10px;">No se encontraron resultados</div>';
}

function renderPerrenSearchResults() {
  const query = (document.getElementById('perrenSearchInput').value || '').toLowerCase();
  const el = document.getElementById('perrenSearchResults');
  if (!el) return;

  const filtered = perrenSqlDatabase.filter(p => !query || p.sku.toLowerCase().includes(query) || p.description.toLowerCase().includes(query) || p.brand.toLowerCase().includes(query));

  let html = '';
  filtered.forEach(p => {
    html += `
      <div class="card" style="padding:8px; cursor:pointer; margin-bottom:6px;" onclick="selectPerrenProduct('${p.sku}')">
        <strong style="font-size:11.5px; color:var(--primary-color);">${p.sku} - ${p.description}</strong>
        <div style="font-size:9.5px; color:#666;">Marca: ${p.brand} | Rubro: ${p.category}</div>
        <div style="font-size:11px; font-weight:bold; color:#D32F2F; margin-top:2px;">Costo Sin IVA: $ ${p.costNoVAT.toLocaleString('es-AR')} ARS</div>
      </div>
    `;
  });
  el.innerHTML = html;
}

function selectPerrenProduct(sku) {
  selectedPerrenProduct = perrenSqlDatabase.find(p => p.sku === sku);
  closePerrenModal();
  updateTab3Comparison();
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

  let allArticles = [];
  localSuppliers.forEach(sup => {
    sup.articles.forEach(art => {
      allArticles.push({ ...art, supplier: sup.companyName });
    });
  });

  document.getElementById('savedCount').textContent = allArticles.length;

  const filtered = allArticles.filter(a => 
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
  let found = null;
  localSuppliers.forEach(sup => {
    sup.articles.forEach(art => {
      if (art.code === code) found = { ...art, supplier: sup.companyName };
    });
  });
  if (found) {
    selectedCantonItem = found;
    switchMainSection('comparador');
    switchTab(3);
  }
}

function sendTab1ToTab3() {
  selectedCantonItem = {
    code: 'MANUAL',
    name: document.getElementById('t1Name').value,
    fob: parseFloat(document.getElementById('t1Fob').value) || 10.0,
    moq: parseInt(document.getElementById('t1Qty').value) || 1000,
    port: document.getElementById('t1Port').value,
    weightKg: parseFloat(document.getElementById('t1Weight').value) || 0.0,
    supplier: 'Carga Manual'
  };
  switchTab(3);
}

function updateTab3Comparison() {
  const item = selectedCantonItem || {
    name: document.getElementById('t1Name').value,
    fob: parseFloat(document.getElementById('t1Fob').value) || 10.0,
    moq: parseInt(document.getElementById('t1Qty').value) || 1000,
    weightKg: parseFloat(document.getElementById('t1Weight').value) || 0.0,
    supplier: 'Carga Manual'
  };

  const perren = selectedPerrenProduct;
  const perrenARS = perren.costNoVAT;
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

  document.getElementById('perrenProductDetail').innerHTML = `
    <strong>${perren.sku} - ${perren.description}</strong><br>
    <span style="color:var(--text-muted);">Marca: ${perren.brand} | Rubro: ${perren.category}</span><br>
    <span style="color:var(--text-muted);">Costo Nacional Sin IVA:</span> 
    <strong id="perrenCostTxt">$ ${perrenARS.toLocaleString('es-AR', {minimumFractionDigits:2})} ARS ($ ${perrenUSD.toFixed(2)} USD)</strong>
  `;

  document.getElementById('cantonProductDetail').innerHTML = `
    <strong>${item.name} (${item.supplier})</strong><br>
    <span style="color:var(--text-muted);">Costo Puesto Incidido Landed:</span> 
    <strong>$ ${unitCostoIncididoARS.toLocaleString('es-AR', {minimumFractionDigits:2})} ARS ($ ${unitCostoIncididoUSD.toFixed(2)} USD)</strong>
  `;

  document.getElementById('savingValTxt').textContent = `$ ${ahorroARS.toLocaleString('es-AR', {minimumFractionDigits:2})} ARS / u ($ ${ahorroUSD.toFixed(2)} USD)`;
  document.getElementById('savingPctTxt').textContent = `Reducción de costo del ${ahorroPct.toFixed(1)}%`;
}

// Modales
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

function openPerrenModal() { document.getElementById('modalPerren').classList.add('open'); renderPerrenSearchResults(); }
function closePerrenModal() { document.getElementById('modalPerren').classList.remove('open'); }

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
