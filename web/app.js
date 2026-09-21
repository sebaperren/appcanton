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
    phone: '+86 138 0000 8888',
    email: 'jacky@foshanceramics.cn',
    articles: [
      { code: 'ART-CN-101', name: 'Cerámico Foshan 60x60 Pulido', fob: 4.50, moq: 2000, port: 'Foshan', weightKg: 3.2 }
    ]
  },
  {
    id: 'SUP-02',
    companyName: 'Zhejiang Sanitary Ware Co.',
    stand: 'Hall 10.1 - Stand B23',
    category: 'Sanitarios y Grifería',
    contactName: 'Wei Chen',
    phone: '+86 139 1111 2222',
    email: 'sales@zjsanitary.cn',
    articles: [
      { code: 'ART-CN-204', name: 'Inodoro Rimless Monobloc Foshan', fob: 38.00, moq: 300, port: 'Ningbo', weightKg: 28.5 }
    ]
  }
];

let savedArticles = [
  { code: 'ART-CN-101', name: 'Cerámico Foshan 60x60 Pulido', supplier: 'Foshan Ceramics Co.', fob: 4.50, moq: 2000, port: 'Foshan', weightKg: 3.2 },
  { code: 'ART-CN-204', name: 'Inodoro Rimless Monobloc Foshan', supplier: 'Zhejiang Sanitary Ware', fob: 38.00, moq: 300, port: 'Ningbo', weightKg: 28.5 }
];

let expandedCards = new Set();
let selectedCantonItem = null;
let selectedPerrenItem = null;
let activeCategoryFilter = 'TODOS';

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
  checkSession();
  setupEventListeners();
  calculateTab1();
  renderTab2List();
  renderTarjetero();
  renderSecSearchResults();
});

// AUTHENTICATION LOGIC
function checkSession() {
  const user = localStorage.getItem('cantonUser');
  if (user) {
    document.getElementById('loginOverlay').style.display = 'none';
    document.getElementById('userStatusLbl').textContent = 'Usuario: ' + user;
  } else {
    document.getElementById('loginOverlay').style.display = 'flex';
  }
}

function handleLogin() {
  const userSelect = document.getElementById('loginUser').value;
  localStorage.setItem('cantonUser', userSelect);
  document.getElementById('userStatusLbl').textContent = 'Usuario: ' + userSelect;
  document.getElementById('loginOverlay').style.display = 'none';
}

function handleLogout() {
  localStorage.removeItem('cantonUser');
  document.getElementById('loginOverlay').style.display = 'flex';
}

// MAIN SECTION SWITCHING
function switchMainSection(secId) {
  currentSection = secId;
  const sections = ['secInicio', 'secAddProveedor', 'secCostos', 'secComparador', 'secTarjetero'];
  sections.forEach(s => {
    const el = document.getElementById(s);
    if (el) el.style.display = (s === 'sec' + capitalize(secId)) ? 'block' : 'none';
  });

  // Update Bottom Nav Styling
  const navItems = {
    'inicio': 'navInicio',
    'addProveedor': 'navAddProveedor',
    'costos': 'navCostos',
    'comparador': 'navComparador',
    'tarjetero': 'navTarjetero'
  };

  Object.keys(navItems).forEach(k => {
    const btn = document.getElementById(navItems[k]);
    if (btn) {
      if (k === secId) btn.classList.add('active');
      else btn.classList.remove('active');
    }
  });

  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function capitalize(str) {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

// EVENT LISTENERS & CONTROLS
function setupEventListeners() {
  document.getElementById('btnCurrARS').addEventListener('click', () => setCurrency('ARS'));
  document.getElementById('btnCurrUSD').addEventListener('click', () => setCurrency('USD'));
  document.getElementById('btnOpenSettings').addEventListener('click', openSettingsModal);

  ['t1Fob', 't1Qty', 't1Weight'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.addEventListener('input', calculateTab1);
  });
}

function setCurrency(mode) {
  currencyMode = mode;
  document.getElementById('btnCurrARS').classList.toggle('active', mode === 'ARS');
  document.getElementById('btnCurrUSD').classList.toggle('active', mode === 'USD');
  calculateTab1();
  renderTab2List();
  if (selectedPerrenItem) renderComparisonResult();
  renderSecSearchResults();
}

function formatMoney(valUSD) {
  if (currencyMode === 'ARS') {
    const valARS = valUSD * stateSettings.tc;
    return `$${valARS.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ARS`;
  } else {
    return `$${valUSD.toFixed(2)} USD`;
  }
}

// WEIGHT PRORATING TOGGLE
function toggleWeightProrating() {
  weightModeEnabled = !weightModeEnabled;
  const btn = document.getElementById('btnToggleWeightMode');
  if (weightModeEnabled) {
    btn.textContent = '✅ PESO ACTIVO';
    btn.style.backgroundColor = '#E65100';
    btn.style.color = '#FFFFFF';
  } else {
    btn.textContent = 'PROBAR PESO';
    btn.style.backgroundColor = '#FFFFFF';
    btn.style.color = '#E65100';
  }
  calculateTab1();
}

// TAB 1: CALCULO LANDED COMPLETO
function calculateTab1() {
  const fobUSD = parseFloat(document.getElementById('t1Fob').value) || 0;
  const qty = parseInt(document.getElementById('t1Qty').value) || 1;
  const weightKgUnit = parseFloat(document.getElementById('t1Weight').value) || 0;

  const totalWeightKg = qty * weightKgUnit;
  const maxWeightKg = stateSettings.containerMaxWeightKg || 26000;
  const weightPct = ((totalWeightKg / maxWeightKg) * 100).toFixed(1);

  document.getElementById('lblWeightDetail').textContent = 
    `${qty} u. × ${weightKgUnit} kg = ${totalWeightKg.toLocaleString()} kg (${weightPct}% de 26.000 kg cont.)`;

  let freightUSDTotal = stateSettings.flete;
  let freightUSDUnit = freightUSDTotal / qty;

  if (weightModeEnabled && totalWeightKg > 0) {
    const proratedContainerFreight = (totalWeightKg / maxWeightKg) * stateSettings.flete;
    freightUSDUnit = proratedContainerFreight / qty;
    freightUSDTotal = proratedContainerFreight;
  }

  const fobTotal = fobUSD * qty;
  const seguroUnit = stateSettings.incSeguro ? (fobUSD * (stateSettings.seguroPct / 100)) : 0;
  const seguroTotal = seguroUnit * qty;

  const cifUnit = fobUSD + (stateSettings.incFlete ? freightUSDUnit : 0) + seguroUnit;
  const cifTotal = cifUnit * qty;

  const arancelUnit = stateSettings.incArancel ? (cifUnit * (stateSettings.arancelPct / 100)) : 0;
  const arancelTotal = arancelUnit * qty;

  const tasaEstadUnit = stateSettings.incTasaEstad ? (cifUnit * (stateSettings.tasaEstadPct / 100)) : 0;
  const tasaEstadTotal = tasaEstadUnit * qty;

  const baseTaxUnit = cifUnit + arancelUnit + tasaEstadUnit;

  const ivaUnit = stateSettings.incIVA ? (baseTaxUnit * (stateSettings.ivaPct / 100)) : 0;
  const ivaTotal = ivaUnit * qty;

  const ivaAdicUnit = stateSettings.incIVAAdic ? (baseTaxUnit * (stateSettings.ivaAdicPct / 100)) : 0;
  const ivaAdicTotal = ivaAdicUnit * qty;

  const gananciasUnit = stateSettings.incGanancias ? (baseTaxUnit * (stateSettings.gananciasPct / 100)) : 0;
  const gananciasTotal = gananciasUnit * qty;

  const iibbUnit = stateSettings.incIIBB ? (baseTaxUnit * (stateSettings.iibbPct / 100)) : 0;
  const iibbTotal = iibbUnit * qty;

  const despachanteUnit = stateSettings.incDespachante ? (cifUnit * (stateSettings.despachantePct / 100)) : 0;
  const despachanteTotal = despachanteUnit * qty;

  const desembolsoTotalUnit = cifUnit + arancelUnit + tasaEstadUnit + ivaUnit + ivaAdicUnit + gananciasUnit + iibbUnit + despachanteUnit;
  const desembolsoTotalTotal = desembolsoTotalUnit * qty;

  const costoIncididoUnit = cifUnit + arancelUnit + tasaEstadUnit + despachanteUnit;
  const costoIncididoTotal = costoIncididoUnit * qty;

  const rows = [
    { name: '1. PRECIO FOB (China)', unit: fobUSD, total: fobTotal, isHeader: true },
    { name: '2. Flete Marítimo (40\' Contenedor)', unit: stateSettings.incFlete ? freightUSDUnit : 0, total: stateSettings.incFlete ? freightUSDTotal : 0 },
    { name: '3. Seguro Internacional (' + stateSettings.seguroPct + '%)', unit: seguroUnit, total: seguroTotal },
    { name: 'VALOR CIF PUERTO AR', unit: cifUnit, total: cifTotal, isHeader: true },
    { name: '4. Arancel Aduanero NCM (' + stateSettings.arancelPct + '%)', unit: arancelUnit, total: arancelTotal },
    { name: '5. Tasa Estadística (' + stateSettings.tasaEstadPct + '%)', unit: tasaEstadUnit, total: tasaEstadTotal },
    { name: '6. IVA General (' + stateSettings.ivaPct + '%)', unit: ivaUnit, total: ivaTotal },
    { name: '7. IVA Adicional (' + stateSettings.ivaAdicPct + '%)', unit: ivaAdicUnit, total: ivaAdicTotal },
    { name: '8. Percepción Ganancias (' + stateSettings.gananciasPct + '%)', unit: gananciasUnit, total: gananciasTotal },
    { name: '9. Percepción II.BB. (' + stateSettings.iibbPct + '%)', unit: iibbUnit, total: iibbTotal },
    { name: '10. Despachante & Gastos (' + stateSettings.despachantePct + '%)', unit: despachanteUnit, total: despachanteTotal },
    { name: '💰 TOTAL DESEMBOLSO INICIAL', unit: desembolsoTotalUnit, total: desembolsoTotalTotal, isTotal: true },
    { name: '🟢 COSTO INCIDIDO NETO DEPOSITO', unit: costoIncididoUnit, total: costoIncididoTotal, isIncidido: true }
  ];

  renderBreakdownTable(rows);
}

function renderBreakdownTable(rows) {
  const tbody = document.getElementById('tblBreakdownBody');
  if (!tbody) return;

  tbody.innerHTML = rows.map(r => {
    let cls = '';
    if (r.isHeader) cls = 'class="header-row"';
    else if (r.isTotal) cls = 'class="total-row"';
    else if (r.isIncidido) cls = 'class="costo-incidido"';

    return `
      <tr ${cls}>
        <td>${r.name}</td>
        <td>-</td>
        <td style="text-align: right;">${formatMoney(r.unit)}</td>
        <td style="text-align: right;">${formatMoney(r.total)}</td>
      </tr>
    `;
  }).join('');
}

// TAB 2: COTIZACIONES GUARDADAS
function renderTab2List() {
  const container = document.getElementById('savedArticlesList');
  if (!container) return;

  container.innerHTML = savedArticles.map((art, index) => {
    const isExpanded = expandedCards.has(index);
    const landedNetUSD = art.fob * 1.58; // Landed neto aproximado

    return `
      <div class="card" style="border-left: 4px solid var(--secondary-color);">
        <div style="display: flex; justify-content: space-between; align-items: flex-start;">
          <div>
            <strong style="font-size: 12px; color: var(--primary-color);">${art.name}</strong>
            <div style="font-size: 10px; color: var(--text-muted); margin-top: 2px;">
              Fábrica: ${art.supplier} • MOQ: ${art.moq} u. • ${art.port}
            </div>
          </div>
          <div class="fob-tag">FOB: ${formatMoney(art.fob)}</div>
        </div>

        <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 8px; font-size: 10px;">
          <div>Costo Landed Est: <strong style="color: var(--accent-color);">${formatMoney(landedNetUSD)}</strong></div>
          <button class="btn-chip" onclick="toggleCardExpand(${index})">
            ${isExpanded ? '▲ Ocultar Desglose' : '▼ Ver Desglose Landed'}
          </button>
        </div>

        ${isExpanded ? `
          <div style="margin-top: 10px; padding-top: 8px; border-top: 1px dashed #DDD; font-size: 9.5px;">
            <div style="display: flex; justify-content: space-between; padding: 2px 0;"><span>FOB China:</span> <span>${formatMoney(art.fob)}</span></div>
            <div style="display: flex; justify-content: space-between; padding: 2px 0;"><span>Flete Marítimo + Seguro:</span> <span>${formatMoney(art.fob * 0.18)}</span></div>
            <div style="display: flex; justify-content: space-between; padding: 2px 0;"><span>Arancel Aduana (20%) + Estad:</span> <span>${formatMoney(art.fob * 0.23)}</span></div>
            <div style="display: flex; justify-content: space-between; padding: 2px 0;"><span>Despachante & Gastos Puerto:</span> <span>${formatMoney(art.fob * 0.08)}</span></div>
            <div style="display: flex; justify-content: space-between; padding: 4px 0; font-weight: bold; color: var(--accent-color); border-top: 1px solid #CCC;">
              <span>COSTO INCIDIDO DEPOSITO:</span> <span>${formatMoney(landedNetUSD)}</span>
            </div>
            <button class="btn-primary" style="margin-top: 6px;" onclick="selectCantonForCompare(${index})">⚡ Usar para Comparar vs Flexxus BI</button>
          </div>
        ` : ''}
      </div>
    `;
  }).join('');
}

function toggleCardExpand(index) {
  if (expandedCards.has(index)) expandedCards.delete(index);
  else expandedCards.add(index);
  renderTab2List();
}

// SECCIÓN 3: BUSCADOR BASE SQL FLEXXUS PERREN (12.074 ARTÍCULOS)
function renderSecSearchResults() {
  const container = document.getElementById('secSearchResults');
  if (!container) return;

  const query = (document.getElementById('secSearchInput')?.value || '').toLowerCase();
  
  const filtered = perrenSqlDatabase.filter(item => {
    const matchQuery = item.sku.toLowerCase().includes(query) ||
                       item.description.toLowerCase().includes(query) ||
                       item.brand.toLowerCase().includes(query) ||
                       item.category.toLowerCase().includes(query);
    const matchCat = activeCategoryFilter === 'TODOS' || item.category === activeCategoryFilter;
    return matchQuery && matchCat;
  });

  if (filtered.length === 0) {
    container.innerHTML = `<div class="card" style="text-align: center; font-size: 11px; color: var(--text-muted);">No se encontraron artículos en la base SQL para "${query}"</div>`;
    return;
  }

  container.innerHTML = filtered.map(item => {
    const costUSD = item.costNoVAT / stateSettings.tc;
    return `
      <div class="card" style="border-left: 4px solid var(--accent-color);">
        <div style="display: flex; justify-content: space-between; align-items: flex-start;">
          <div>
            <strong style="font-size: 12px; color: var(--primary-color);">${item.description}</strong>
            <div style="font-size: 10px; color: var(--text-muted); margin-top: 2px;">
              SKU: ${item.sku} • Marca: ${item.brand} • Rubro: ${item.category} • Clase: <span class="badge" style="background:#E3F2FD; color:#1565C0;">${item.abcClass}</span>
            </div>
          </div>
          <div class="fob-tag" style="background: #E8F5E9; color: var(--accent-color);">
            ${formatMoney(costUSD)}
          </div>
        </div>

        <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 8px;">
          <div style="font-size: 9.5px; color: var(--text-muted);">Costo Neto Flexxus ARS: $${item.costNoVAT.toLocaleString('es-AR')}</div>
          <button class="btn-chip" onclick="selectPerrenItemFromSec('${item.sku}')" style="background: var(--secondary-color); color: white; border: none;">
            ⚖️ Comparar vs Canton
          </button>
        </div>
      </div>
    `;
  }).join('');
}

function filterCategory(btnEl, category) {
  activeCategoryFilter = category;
  document.querySelectorAll('.filter-chip').forEach(c => c.classList.remove('selected'));
  btnEl.classList.add('selected');
  renderSecSearchResults();
}

function selectPerrenItemFromSec(sku) {
  selectedPerrenItem = perrenSqlDatabase.find(i => i.sku === sku);
  switchMainSection('comparador');
  switchTab(3);
  renderSelectedPerrenCard();
  renderComparisonResult();
}

// TAB 3: COMPARADOR EN VIVO
function selectCantonForCompare(index) {
  selectedCantonItem = savedArticles[index];
  switchTab(3);
  renderComparisonResult();
}

function switchTab(tabNum) {
  currentTab = tabNum;
  document.getElementById('tab1Content').style.display = tabNum === 1 ? 'block' : 'none';
  document.getElementById('tab2Content').style.display = tabNum === 2 ? 'block' : 'none';
  document.getElementById('tab3Content').style.display = tabNum === 3 ? 'block' : 'none';

  [1, 2, 3].forEach(i => {
    const btn = document.getElementById('tabBtn' + i);
    if (btn) {
      if (i === tabNum) btn.classList.add('active');
      else btn.classList.remove('active');
    }
  });
}

// TARJETERO & WHATSAPP DIRECTO
function renderTarjetero() {
  const container = document.getElementById('tarjeteroList');
  if (!container) return;

  container.innerHTML = localSuppliers.map(sup => {
    const cleanPhone = sup.phone.replace(/[^0-9]/g, '');
    const waLink = `https://wa.me/${cleanPhone}?text=Hola%20${encodeURIComponent(sup.contactName)},%20te%20contacto%20desde%20Perren%20%26%20C%C3%ADa.%20por%20la%20Feria%20de%20Cant%C3%B3n`;

    return `
      <div class="card" style="border-left: 4px solid #25D366;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start;">
          <div>
            <strong style="font-size: 13px; color: var(--primary-color);">${sup.companyName}</strong>
            <div style="font-size: 10.5px; color: var(--text-muted); margin-top: 2px;">
              📍 Stand: ${sup.stand} • Rubro: ${sup.category}
            </div>
            <div style="font-size: 11px; margin-top: 6px;">
              👤 <strong>${sup.contactName}</strong> (${sup.phone})
            </div>
          </div>
          <a href="${waLink}" target="_blank" class="btn-whatsapp">
            💬 WhatsApp
          </a>
        </div>
      </div>
    `;
  }).join('');
}

// ALTA DE PROVEEDORES DESDE FORMULARIO
function saveSupplierFromForm() {
  const name = document.getElementById('pCompany').value;
  const stand = document.getElementById('pStand').value;
  const cat = document.getElementById('pCategory').value;
  const contact = document.getElementById('pContact').value;
  const phone = document.getElementById('pPhone').value;
  const email = document.getElementById('pEmail').value;

  if (!name) {
    alert('Por favor ingresa el nombre de la empresa');
    return;
  }

  localSuppliers.unshift({
    id: 'SUP-' + (localSuppliers.length + 1),
    companyName: name,
    stand: stand || 'Stand s/d',
    category: cat || 'General',
    contactName: contact || 'Contacto',
    phone: phone || '+86',
    email: email || '',
    articles: []
  });

  document.getElementById('lblTotalSuppliers').textContent = localSuppliers.length;
  renderTarjetero();
  alert('✅ Proveedor guardado correctamente en la base local!');
  switchMainSection('tarjetero');
}

// MODAL Y BÚSQUEDA EN PERREN (SQL)
function openPerrenModal() {
  document.getElementById('modalPerren').classList.add('open');
  renderPerrenSearchResults();
}

function closePerrenModal() {
  document.getElementById('modalPerren').classList.remove('open');
}

function renderPerrenSearchResults() {
  const container = document.getElementById('perrenSearchResults');
  if (!container) return;

  const query = (document.getElementById('perrenSearchInput')?.value || '').toLowerCase();
  const filtered = perrenSqlDatabase.filter(i => 
    i.sku.toLowerCase().includes(query) || i.description.toLowerCase().includes(query) || i.brand.toLowerCase().includes(query)
  );

  container.innerHTML = filtered.map(item => `
    <div style="padding: 8px; border-bottom: 1px solid #EEE; display: flex; justify-content: space-between; align-items: center;">
      <div>
        <strong style="font-size: 11px; color: var(--primary-color);">${item.description}</strong>
        <div style="font-size: 9.5px; color: var(--text-muted);">${item.sku} • ${item.brand}</div>
      </div>
      <button class="btn-chip" onclick="selectPerrenFromModal('${item.sku}')">Seleccionar</button>
    </div>
  `).join('');
}

function selectPerrenFromModal(sku) {
  selectedPerrenItem = perrenSqlDatabase.find(i => i.sku === sku);
  closePerrenModal();
  renderSelectedPerrenCard();
  renderComparisonResult();
}

function renderSelectedPerrenCard() {
  const container = document.getElementById('selectedPerrenCard');
  if (!container || !selectedPerrenItem) return;

  const costUSD = selectedPerrenItem.costNoVAT / stateSettings.tc;

  container.innerHTML = `
    <div style="background-color: #FFEBEE; padding: 10px; border-radius: 8px; border-left: 4px solid #D32F2F;">
      <div style="font-size: 10px; color: #D32F2F; font-weight: bold;">🇦🇷 ARTÍCULO SELECCIONADO PERREN FLEXXUS:</div>
      <strong style="font-size: 12px;">${selectedPerrenItem.description}</strong>
      <div style="font-size: 10.5px; margin-top: 4px;">
        Costo Neto (Sin IVA): <strong>$${selectedPerrenItem.costNoVAT.toLocaleString('es-AR')} ARS</strong> (${formatMoney(costUSD)})
      </div>
    </div>
  `;
}

function renderComparisonResult() {
  const container = document.getElementById('comparisonResultCard');
  if (!container) return;

  const chinaItem = selectedCantonItem || savedArticles[1];
  const perrenItem = selectedPerrenItem || perrenSqlDatabase[3];

  const chinaLandedUSD = chinaItem.fob * 1.58;
  const perrenCostUSD = perrenItem.costNoVAT / stateSettings.tc;

  const diffUSD = perrenCostUSD - chinaLandedUSD;
  const diffPct = ((diffUSD / perrenCostUSD) * 100).toFixed(1);
  const containerProfitUSD = diffUSD * (chinaItem.moq || 300);

  container.style.display = 'block';
  container.innerHTML = `
    <h3 style="font-size: 13px; color: var(--primary-color); margin-bottom: 8px;">⚡ RESULTADO COMPARATIVO</h3>
    
    <div class="row-2">
      <div style="background: #FFEBEE; padding: 8px; border-radius: 6px; font-size: 10px;">
        <strong>🇦🇷 Perren Argentina:</strong><br>
        Costo Neto: ${formatMoney(perrenCostUSD)}
      </div>
      <div style="background: #E8F5E9; padding: 8px; border-radius: 6px; font-size: 10px;">
        <strong>🇨🇳 Canton Landed:</strong><br>
        Costo Puesto: ${formatMoney(chinaLandedUSD)}
      </div>
    </div>

    <div style="margin-top: 10px; background: #E3F2FD; padding: 10px; border-radius: 8px; text-align: center;">
      <div style="font-size: 11px; color: var(--secondary-color); font-weight: bold;">🎯 AHORRO DIRECTO ESTIMADO:</div>
      <div style="font-size: 20px; font-weight: bold; color: var(--accent-color); margin: 2px 0;">
        +${diffPct}% (${formatMoney(diffUSD)} / u.)
      </div>
      <div style="font-size: 10px; color: var(--text-muted);">
        Ganancia estimada por embarque (${chinaItem.moq} u.): <strong style="color: var(--accent-color);">${formatMoney(containerProfitUSD)}</strong>
      </div>
    </div>
  `;
}

// MODAL AJUSTES
function openSettingsModal() {
  document.getElementById('modalSettings').classList.add('open');
}

function closeSettingsModal() {
  stateSettings.tc = parseFloat(document.getElementById('sTC').value) || 1350;
  stateSettings.flete = parseFloat(document.getElementById('sFlete').value) || 3400;
  stateSettings.seguroPct = parseFloat(document.getElementById('sSeguro').value) || 1.2;
  stateSettings.arancelPct = parseFloat(document.getElementById('sArancel').value) || 20;
  stateSettings.tasaEstadPct = parseFloat(document.getElementById('sTasaEstad').value) || 3;
  stateSettings.despachantePct = parseFloat(document.getElementById('sDespachante').value) || 8;
  stateSettings.ivaPct = parseFloat(document.getElementById('sIVA').value) || 21;
  stateSettings.ivaAdicPct = parseFloat(document.getElementById('sIVAAdic').value) || 20;
  stateSettings.gananciasPct = parseFloat(document.getElementById('sGanancias').value) || 6;
  stateSettings.iibbPct = parseFloat(document.getElementById('sIIBB').value) || 2.5;

  document.getElementById('lblDolarTC').textContent = `Dólar TC: $${stateSettings.tc.toLocaleString('es-AR', { minimumFractionDigits: 2 })} ARS`;

  document.getElementById('modalSettings').classList.remove('open');
  calculateTab1();
  renderTab2List();
}

function toggleInc(key) {
  stateSettings[key] = !stateSettings[key];
  const chip = document.getElementById('chip_' + key);
  if (chip) chip.classList.toggle('selected', stateSettings[key]);
  calculateTab1();
}

// MODAL CÁMARA OCR
function openOcrCamera() {
  document.getElementById('modalOcr').classList.add('open');
}

function closeOcrCamera() {
  document.getElementById('modalOcr').classList.remove('open');
}

function processOcrImage(event) {
  const file = event.target.files[0];
  if (!file) return;

  const status = document.getElementById('ocrStatus');
  const output = document.getElementById('ocrOutput');

  status.textContent = '⏳ Analizando imagen con OCR (Tesseract.js)... Por favor aguarda...';
  output.textContent = '';

  Tesseract.recognize(file, 'eng+spa', {
    logger: m => {
      if (m.status === 'recognizing text') {
        status.textContent = `⏳ Procesando texto OCR: ${Math.round(m.progress * 100)}%`;
      }
    }
  }).then(({ data: { text } }) => {
    status.textContent = '✅ Texto extraído correctamente:';
    output.textContent = text;

    // Save image to IndexedDB
    const reader = new FileReader();
    reader.onload = (e) => {
      if (db) {
        const tx = db.transaction('photos', 'readwrite');
        tx.objectStore('photos').add({ image: e.target.result, date: new Date().toISOString(), text });
      }
    };
    reader.readAsDataURL(file);

    // Auto-detect numbers / prices
    const matches = text.match(/\$?\d+[\.,]\d{2}/g);
    if (matches && matches.length > 0) {
      const detectedPrice = parseFloat(matches[0].replace('$', '').replace(',', '.'));
      if (detectedPrice > 0) {
        document.getElementById('t1Fob').value = detectedPrice;
        calculateTab1();
      }
    }
  }).catch(err => {
    status.textContent = '❌ Error al procesar la imagen: ' + err.message;
  });
}
