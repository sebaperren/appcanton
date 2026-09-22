// FIREBASE CONFIGURATION (Proyecto perrenycia-crm)
const firebaseConfig = {
  apiKey: "AIzaSyACSEsLCb5CIF1kuKw_pCLKabTIc7oDDE0",
  authDomain: "perrenycia-crm.firebaseapp.com",
  projectId: "perrenycia-crm",
  storageBucket: "perrenycia-crm.firebasestorage.app",
  messagingSenderId: "695923349499",
  appId: "1:695923349499:web:497eef0db65c29751d1d5e"
};

// Initialize Firebase App & Services
let fbApp, fbAuth, fbDb;
try {
  if (typeof firebase !== 'undefined') {
    fbApp = firebase.initializeApp(firebaseConfig);
    fbAuth = firebase.auth();
    fbDb = firebase.firestore();
    fbDb.enablePersistence({ synchronizeTabs: true }).catch(err => console.log("Persistence notice:", err.code));
  }
} catch (e) {
  console.log("Firebase init note:", e);
}

// State Global
let currentSection = 'inicio';
let currentTab = 1;
let currencyMode = 'USD'; // Default to 'USD'
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

// Base de Datos Perren Flexxus BI (Se carga dinámicamente desde articulos_flexxus_perren.csv - 12.074 artículos)
let perrenSqlDatabase = [];

// Proveedores Guardados y Cotizaciones Chinas (Se cargan dinámicamente desde Firestore / IndexedDB)
let localSuppliers = [];
let savedArticles = [];

let expandedCards = new Set();
let selectedCantonItem = null;
let selectedPerrenItem = null;
let activeCategoryFilter = 'TODOS';

// IndexedDB Storage setup for Photo Backup & 12.074 Flexxus CSV Products
let db;
const request = indexedDB.open('CantonAppDB', 2);
request.onupgradeneeded = (e) => {
  db = e.target.result;
  if (!db.objectStoreNames.contains('photos')) {
    db.createObjectStore('photos', { keyPath: 'id', autoIncrement: true });
  }
  if (!db.objectStoreNames.contains('flexxus_products')) {
    db.createObjectStore('flexxus_products', { keyPath: 'sku' });
  }
};
request.onsuccess = (e) => { 
  db = e.target.result; 
  loadFlexxusCsvDatabase();
};
request.onerror = () => {
  loadFlexxusCsvDatabase();
};

// Initialize PWA Service Worker
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('./sw.js').catch(err => console.log('SW reg error', err));
}

document.addEventListener('DOMContentLoaded', () => {
  initFirebaseAuthListener();
  initFirestoreSuppliersListener();
  setupEventListeners();
  calculateTab1();
  renderTab2List();
  renderTarjetero();
  setTimeout(() => loadFlexxusCsvDatabase(), 100);
});

function initFirestoreSuppliersListener() {
  if (!fbDb) return;
  fbDb.collection('suppliers').onSnapshot(snapshot => {
    const firestoreSuppliers = [];
    snapshot.forEach(doc => {
      const data = doc.data();
      firestoreSuppliers.push({ ...data, firestoreId: doc.id });
    });
    if (firestoreSuppliers.length > 0) {
      firestoreSuppliers.forEach(fsSup => {
        const idx = localSuppliers.findIndex(ls => ls.id === fsSup.id || (ls.companyName && ls.companyName.toLowerCase().trim() === fsSup.companyName.toLowerCase().trim()));
        if (idx >= 0) {
          localSuppliers[idx] = fsSup;
        } else {
          localSuppliers.unshift(fsSup);
        }
      });
      const lblSuppliers = document.getElementById('lblTotalSuppliers');
      if (lblSuppliers) lblSuppliers.textContent = localSuppliers.length;
      renderTarjetero();
    }
  }, err => {
    console.log('Firestore suppliers listener notice:', err);
  });
}

// CSV PARSER & INDEXEDDB CACHING LOGIC
function parseCSVLine(line) {
  const result = [];
  let current = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (char === '"') {
      inQuotes = !inQuotes;
    } else if (char === ',' && !inQuotes) {
      result.push(current.trim());
      current = '';
    } else {
      current += char;
    }
  }
  result.push(current.trim());
  return result;
}

async function loadFlexxusCsvDatabase() {
  const statusEl = document.getElementById('flexxusDbStatus');
  if (statusEl) statusEl.textContent = '⏳ Cargando catálogo Flexxus (12.074 artículos)...';

  try {
    // 1. Intentar cargar desde IndexedDB local primero
    const cachedProducts = await getProductsFromIndexedDB();
    if (cachedProducts && cachedProducts.length > 5000) {
      perrenSqlDatabase = cachedProducts;
      if (statusEl) statusEl.textContent = `🟢 Base SQL Flexxus BI: ${perrenSqlDatabase.length.toLocaleString('es-AR')} artículos listos (Offline)`;
      renderCategoryChips();
      renderSecSearchResults();
      return;
    }

    // 2. Probar rutas posibles para articulos_flexxus_perren.csv
    let response = null;
    const paths = ['articulos_flexxus_perren.csv', './articulos_flexxus_perren.csv', 'web/articulos_flexxus_perren.csv', './web/articulos_flexxus_perren.csv'];
    
    for (const p of paths) {
      try {
        const res = await fetch(p);
        if (res.ok) {
          response = res;
          break;
        }
      } catch (_) {}
    }

    if (!response) {
      throw new Error('Archivo CSV no encontrado');
    }

    const text = await response.text();
    const lines = text.split(/\r?\n/);

    const parsed = [];
    for (let i = 1; i < lines.length; i++) {
      const line = lines[i].trim();
      if (!line) continue;
      const cols = parseCSVLine(line);
      if (cols.length >= 8) {
        parsed.push({
          sku: cols[0],
          description: cols[1],
          brand: cols[2] || 'Varios',
          category: cols[3] || 'General',
          subcategory: cols[4] || '',
          classification: cols[5] || '',
          abcClass: cols[6] || 'A',
          costNoVAT: parseFloat(cols[7]) || 0.0,
          salePriceNoVAT: parseFloat(cols[8]) || 0.0,
          stockAvailable: parseInt(cols[9]) || 0,
          unit: cols[10] || 'Unidad'
        });
      }
    }

    if (parsed.length > 0) {
      perrenSqlDatabase = parsed;
      if (statusEl) statusEl.textContent = `🟢 Base SQL Flexxus BI: ${perrenSqlDatabase.length.toLocaleString('es-AR')} artículos cargados`;
      saveProductsToIndexedDB(parsed);
      renderCategoryChips();
      renderSecSearchResults();
    }
  } catch (err) {
    console.log('Flexxus CSV Notice:', err);
    if (statusEl) statusEl.textContent = `🟢 Base SQL Flexxus BI: ${perrenSqlDatabase.length.toLocaleString('es-AR')} artículos listos`;
    renderCategoryChips();
    renderSecSearchResults();
  }
}

function saveProductsToIndexedDB(products) {
  if (!db) return;
  try {
    const tx = db.transaction('flexxus_products', 'readwrite');
    const store = tx.objectStore('flexxus_products');
    store.clear();
    products.forEach(p => store.put(p));
  } catch (e) {
    console.log('IndexedDB save notice:', e);
  }
}

function getProductsFromIndexedDB() {
  return new Promise((resolve) => {
    if (!db) return resolve([]);
    try {
      if (!db.objectStoreNames.contains('flexxus_products')) return resolve([]);
      const tx = db.transaction('flexxus_products', 'readonly');
      const store = tx.objectStore('flexxus_products');
      const req = store.getAll();
      req.onsuccess = () => resolve(req.result || []);
      req.onerror = () => resolve([]);
    } catch (e) {
      resolve([]);
    }
  });
}

function renderCategoryChips() {
  const container = document.getElementById('categoryChipsContainer');
  if (!container) return;

  const categories = new Set();
  perrenSqlDatabase.forEach(p => {
    if (p.category && p.category.trim() && p.category !== 'General' && p.category !== '35"') {
      categories.add(p.category.trim());
    }
  });

  const sortedCat = Array.from(categories).sort().slice(0, 15);

  container.innerHTML = `
    <span class="filter-chip ${activeCategoryFilter === 'TODOS' ? 'selected' : ''}" onclick="filterCategory(this, 'TODOS')">Todos</span>
  ` + sortedCat.map(cat => {
    const isSelected = activeCategoryFilter === cat ? 'selected' : '';
    const safeCat = cat.replace(/'/g, "\\'");
    return `<span class="filter-chip ${isSelected}" onclick="filterCategory(this, '${safeCat}')">${cat}</span>`;
  }).join('');
}

// STRICT AUTHENTICATION DISPLAY CONTROL
function showAuthenticatedApp(userEmail) {
  const loginScreen = document.getElementById('loginScreen');
  const appContainer = document.getElementById('appContainer');
  const userStatusLbl = document.getElementById('userStatusLbl');

  if (loginScreen) loginScreen.style.display = 'none';
  if (appContainer) appContainer.style.display = 'block';
  if (userStatusLbl) userStatusLbl.textContent = 'Firebase User: ' + userEmail;
}

function showUnauthenticatedLogin() {
  const loginScreen = document.getElementById('loginScreen');
  const appContainer = document.getElementById('appContainer');

  if (loginScreen) loginScreen.style.display = 'flex';
  if (appContainer) appContainer.style.display = 'none';
}

function initFirebaseAuthListener() {
  if (fbAuth) {
    fbAuth.onAuthStateChanged((user) => {
      if (user) {
        localStorage.setItem('firebaseAuthSession', user.email);
        showAuthenticatedApp(user.email);
      } else {
        localStorage.removeItem('firebaseAuthSession');
        showUnauthenticatedLogin();
      }
    });
  } else {
    showUnauthenticatedLogin();
  }
}

function handleFirebaseAuthLogin() {
  const emailInput = document.getElementById('fbEmail');
  const passwordInput = document.getElementById('fbPassword');
  const statusEl = document.getElementById('fbAuthStatus');

  const email = (emailInput?.value || '').trim();
  const password = (passwordInput?.value || '').trim();

  if (!email || !password) {
    alert('Por favor ingrese correo electrónico y contraseña.');
    return;
  }

  if (statusEl) statusEl.textContent = '⏳ Verificando credenciales en Firebase Cloud...';

  if (fbAuth) {
    fbAuth.signInWithEmailAndPassword(email, password)
      .then((userCredential) => {
        const user = userCredential.user;
        localStorage.setItem('firebaseAuthSession', user.email || email);
        if (statusEl) statusEl.textContent = '🟢 Autenticado con Firebase: ' + (user.email || email);
        showAuthenticatedApp(user.email || email);
      })
      .catch((error) => {
        console.error("Firebase Auth Error:", error.code, error.message);
        let errorMsg = 'Error de inicio de sesión';
        if (error.code === 'auth/user-not-found') {
          errorMsg = '❌ El correo electrónico no está registrado en Firebase. Hacé clic en CREAR CUENTA.';
        } else if (error.code === 'auth/wrong-password' || error.code === 'auth/invalid-credential') {
          errorMsg = '❌ Contraseña incorrecta. Verificá tu clave de Firebase.';
        } else if (error.code === 'auth/invalid-email') {
          errorMsg = '❌ Formato de correo electrónico no válido.';
        } else if (error.code === 'auth/too-many-requests') {
          errorMsg = '⚠️ Demasiados intentos fallidos. Aguardá unos instantes.';
        } else {
          errorMsg = '❌ Error de autenticación: ' + error.message;
        }

        if (statusEl) statusEl.textContent = errorMsg;
        alert(errorMsg);
      });
  } else {
    alert('❌ Error: El servicio Firebase Auth no está activo. Verificá tu conexión a internet.');
  }
}

function handleFirebaseRegister() {
  const emailInput = document.getElementById('fbEmail');
  const passwordInput = document.getElementById('fbPassword');
  const statusEl = document.getElementById('fbAuthStatus');

  const email = (emailInput?.value || '').trim();
  const password = (passwordInput?.value || '').trim();

  if (!email || !password) {
    alert('Por favor ingrese correo electrónico y contraseña para registrarse.');
    return;
  }

  if (password.length < 6) {
    alert('La contraseña de Firebase debe tener al menos 6 caracteres.');
    return;
  }

  if (statusEl) statusEl.textContent = '⏳ Registrando nuevo usuario en Firebase Cloud...';

  if (fbAuth) {
    fbAuth.createUserWithEmailAndPassword(email, password)
      .then((userCredential) => {
        const user = userCredential.user;
        localStorage.setItem('firebaseAuthSession', user.email || email);
        alert('✅ Usuario creado y autenticado correctamente en Firebase!');
        showAuthenticatedApp(user.email || email);
      })
      .catch((error) => {
        let errStr = 'Error al registrar: ';
        if (error.code === 'auth/email-already-in-use') {
          errStr = '⚠️ El correo electrónico ya está registrado. Tocá INICIAR SESIÓN.';
        } else {
          errStr += error.message;
        }
        if (statusEl) statusEl.textContent = errStr;
        alert(errStr);
      });
  } else {
    alert('❌ Error: Servicio de Firebase no disponible.');
  }
}

function handleFirebaseLogout() {
  localStorage.removeItem('firebaseAuthSession');
  if (fbAuth) {
    fbAuth.signOut().catch(err => console.log('Logout notice', err));
  }
  showUnauthenticatedLogin();
}

// MAIN SECTION SWITCHING
function switchMainSection(secId) {
  currentSection = secId;
  const sections = ['secInicio', 'secAddProveedor', 'secCostos', 'secComparador', 'secTarjetero'];
  sections.forEach(s => {
    const el = document.getElementById(s);
    if (el) el.style.display = (s === 'sec' + capitalize(secId)) ? 'block' : 'none';
  });

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

  if (secId === 'costos') {
    renderSecSearchResults();
  }

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

// CÁLCULO GENERAL DE DESGLOSE DE COSTO LANDED PUESTO EN DEPOSITO
function calculateLandedBreakdown(fobUSD, qty, weightKg) {
  qty = parseInt(qty) || 1;
  weightKg = parseFloat(weightKg) || 0;
  fobUSD = parseFloat(fobUSD) || 0;

  const totalWeightKg = qty * weightKg;
  const maxWeightKg = stateSettings.containerMaxWeightKg || 26000;

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

  return {
    fobUSD,
    qty,
    weightKg,
    totalWeightKg,
    costoIncididoUnit,
    costoIncididoTotal,
    desembolsoTotalUnit,
    desembolsoTotalTotal,
    rows: [
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
    ]
  };
}

// TAB 1: CALCULO LANDED COMPLETO
function calculateTab1() {
  const fobUSD = parseFloat(document.getElementById('t1Fob').value) || 0;
  const qty = parseInt(document.getElementById('t1Qty').value) || 1;
  const weightKgUnit = parseFloat(document.getElementById('t1Weight').value) || 0;

  const res = calculateLandedBreakdown(fobUSD, qty, weightKgUnit);
  const maxWeightKg = stateSettings.containerMaxWeightKg || 26000;
  const weightPct = ((res.totalWeightKg / maxWeightKg) * 100).toFixed(1);

  const lblWeight = document.getElementById('lblWeightDetail');
  if (lblWeight) {
    lblWeight.textContent = 
      `${qty} u. × ${weightKgUnit} kg = ${res.totalWeightKg.toLocaleString()} kg (${weightPct}% de 26.000 kg cont.)`;
  }

  renderBreakdownTable(res.rows);
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

// TAB 2: COTIZACIONES GUARDADAS CON EDICIÓN DE CAMPOS Y TABLA LANDED COMPLETA
function renderTab2List() {
  const container = document.getElementById('savedArticlesList');
  if (!container) return;

  const countSpan = document.getElementById('savedCount');
  if (countSpan) countSpan.textContent = savedArticles.length;

  if (savedArticles.length === 0) {
    container.innerHTML = `<div class="card" style="text-align: center; font-size: 11px; color: var(--text-muted);">No hay cotizaciones guardadas aún.</div>`;
    return;
  }

  container.innerHTML = savedArticles.map((art, index) => {
    const isExpanded = expandedCards.has(index);
    const weightVal = art.weightKg || 3.2;
    const breakdown = calculateLandedBreakdown(art.fob, art.moq, weightVal);

    return `
      <div class="card" style="border-left: 4px solid var(--secondary-color); margin-bottom: 15px;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start;">
          <div>
            <strong style="font-size: 13px; color: var(--primary-color);">${art.name}</strong>
            <div style="font-size: 10px; color: var(--text-muted); margin-top: 2px;">
              Fábrica: <strong>${art.supplier || 'Feria Cantón'}</strong> • MOQ: ${art.moq} u. • ${art.port || 'China'}
            </div>
          </div>
          <div class="fob-tag" style="background: #E3F2FD; color: var(--secondary-color); font-weight: bold; padding: 4px 8px; border-radius: 6px; font-size: 11px;">
            FOB: ${formatMoney(art.fob)}
          </div>
        </div>

        <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 8px; font-size: 11px; background: #E8F5E9; padding: 8px; border-radius: 6px;">
          <div>Costo Landed Est: <strong style="color: var(--accent-color); font-size: 12.5px;">${formatMoney(breakdown.costoIncididoUnit)}</strong> / u.</div>
          <button class="btn-chip" onclick="toggleCardExpand(${index})" style="background: var(--primary-color); color: white; border: none; padding: 4px 8px;">
            ${isExpanded ? '▲ Ocultar Editar / Desglose' : '▼ Editar Datos / Ver Desglose Landed'}
          </button>
        </div>

        ${isExpanded ? `
          <div style="margin-top: 12px; padding-top: 10px; border-top: 1px dashed #CCC;">
            
            <strong style="font-size: 11px; color: var(--primary-color);">✏️ EDITAR PARÁMETROS DEL ARTÍCULO GUARDADO</strong>
            
            <div class="row-2" style="margin-top: 6px;">
              <div class="form-group">
                <label style="font-size: 10px;">Precio FOB Unitario (USD)</label>
                <input type="number" step="0.01" value="${art.fob}" onchange="updateSavedArticleField(${index}, 'fob', this.value)" class="form-control" style="font-size: 11px;">
              </div>
              <div class="form-group">
                <label style="font-size: 10px;">Cantidad / MOQ (Unidades)</label>
                <input type="number" value="${art.moq}" onchange="updateSavedArticleField(${index}, 'moq', this.value)" class="form-control" style="font-size: 11px;">
              </div>
            </div>

            <div class="row-2">
              <div class="form-group">
                <label style="font-size: 10px;">Peso Unitario (kg)</label>
                <input type="number" step="0.1" value="${weightVal}" onchange="updateSavedArticleField(${index}, 'weightKg', this.value)" class="form-control" style="font-size: 11px;">
              </div>
              <div class="form-group">
                <label style="font-size: 10px;">Puerto de Origen</label>
                <input type="text" value="${art.port || 'Foshan, China'}" onchange="updateSavedArticleField(${index}, 'port', this.value)" class="form-control" style="font-size: 11px;">
              </div>
            </div>

            <!-- Tabla Desglose Completo Landed (Idéntica a Foto 1) -->
            <div style="margin-top: 10px;">
              <strong style="font-size: 11px; color: var(--primary-color);">📊 DESGLOSE DE COSTO LANDED PUESTO EN DEPOSITO</strong>
              <table class="breakdown-table" style="margin-top: 6px; width: 100%;">
                <thead>
                  <tr>
                    <th>CONCEPTO TRIBUTARIO</th>
                    <th>%</th>
                    <th style="text-align: right;">UNITARIO</th>
                    <th style="text-align: right;">TOTAL EMBARQUE</th>
                  </tr>
                </thead>
                <tbody>
                  ${breakdown.rows.map(r => {
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
                  }).join('')}
                </tbody>
              </table>
            </div>

            <button class="btn-primary" style="margin-top: 10px; width: 100%;" onclick="selectCantonForCompare(${index})">⚡ Usar para Comparar vs Flexxus BI</button>
          </div>
        ` : ''}
      </div>
    `;
  }).join('');
}

function updateSavedArticleField(index, field, value) {
  if (!savedArticles[index]) return;
  if (field === 'fob' || field === 'weightKg') {
    savedArticles[index][field] = parseFloat(value) || 0;
  } else if (field === 'moq') {
    savedArticles[index][field] = parseInt(value) || 1;
  } else {
    savedArticles[index][field] = value;
  }
  renderTab2List();
}

function toggleCardExpand(index) {
  if (expandedCards.has(index)) expandedCards.delete(index);
  else expandedCards.add(index);
  renderTab2List();
}

// SECCIÓN 3: BUSCADOR EN BASE SQL FLEXXUS (12.074 ARTÍCULOS REALES DEL CSV)
function renderSecSearchResults() {
  const container = document.getElementById('secSearchResults');
  if (!container) return;

  const rawQuery = (document.getElementById('secSearchInput')?.value || '').toLowerCase().trim();
  
  if (!rawQuery && activeCategoryFilter === 'TODOS') {
    container.innerHTML = `
      <div class="card" style="text-align: center; font-size: 11px; color: var(--text-muted); padding: 15px;">
        🔍 Escribe en el buscador arriba (SKU, descripción o marca) o selecciona un rubro para consultar la base SQL de Perren (${perrenSqlDatabase.length.toLocaleString('es-AR')} artículos).
      </div>
    `;
    return;
  }

  const filtered = perrenSqlDatabase.filter(item => {
    const matchQuery = !rawQuery || 
                       (item.sku && item.sku.toLowerCase().includes(rawQuery)) ||
                       (item.description && item.description.toLowerCase().includes(rawQuery)) ||
                       (item.brand && item.brand.toLowerCase().includes(rawQuery)) ||
                       (item.category && item.category.toLowerCase().includes(rawQuery));
    const matchCat = activeCategoryFilter === 'TODOS' || 
                     (item.category && item.category.toLowerCase().trim() === activeCategoryFilter.toLowerCase().trim());
    return matchQuery && matchCat;
  });

  if (filtered.length === 0) {
    container.innerHTML = `<div class="card" style="text-align: center; font-size: 11px; color: var(--text-muted);">No se encontraron artículos en la base para "${rawQuery}"</div>`;
    return;
  }

  // Slice to 50 for max UI performance
  const displayItems = filtered.slice(0, 50);

  container.innerHTML = `
    <div style="font-size: 10px; color: var(--text-muted); margin-bottom: 6px; padding: 0 4px;">
      Mostrando ${displayItems.length} de ${filtered.length.toLocaleString('es-AR')} resultados:
    </div>
  ` + displayItems.map(item => {
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
          <div style="font-size: 9.5px; color: var(--text-muted);">Costo Neto Flexxus ARS: $${item.costNoVAT.toLocaleString('es-AR', { minimumFractionDigits: 2 })}</div>
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
  document.querySelectorAll('#categoryChipsContainer .filter-chip').forEach(c => c.classList.remove('selected'));
  if (btnEl) btnEl.classList.add('selected');
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

  if (!localSuppliers || localSuppliers.length === 0) {
    container.innerHTML = `<div class="card" style="text-align: center; font-size: 11px; color: var(--text-muted);">No hay proveedores guardados aún. Ve a <strong>"+ Proveedor"</strong> para escanear tarjetas o ingresar datos.</div>`;
    return;
  }

  container.innerHTML = localSuppliers.map(sup => {
    const cleanPhone = (sup.phone || '').replace(/[^0-9]/g, '');
    const waLink = `https://wa.me/${cleanPhone}?text=Hola%20${encodeURIComponent(sup.contactName || 'contacto')},%20te%20contacto%20desde%20Perren%20%26%20C%C3%ADa.%20por%20la%20Feria%20de%20Cant%C3%B3n`;
    const articles = sup.articles || [];

    return `
      <div class="card" style="border-left: 4px solid #25D366; margin-bottom: 12px;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start;">
          <div>
            <strong style="font-size: 14px; color: var(--primary-color);">${sup.companyName}</strong>
            ${sup.companyNameChinese ? `<div style="font-size: 11px; color: var(--secondary-color); font-weight: bold;">🇨🇳 ${sup.companyNameChinese}</div>` : ''}
            
            <div style="font-size: 10.5px; color: var(--text-muted); margin-top: 4px;">
              📍 <strong>Stand:</strong> ${sup.stand || 's/d'} • 🏷️ <strong>Rubro:</strong> ${sup.category || 'General'}
            </div>
            
            <div style="font-size: 11px; margin-top: 6px;">
              👤 <strong>Contacto:</strong> ${sup.contactName || 's/d'} ${sup.weChat ? `(WeChat: <strong>${sup.weChat}</strong>)` : ''}
            </div>

            <div style="font-size: 10.5px; color: #555; margin-top: 2px;">
              📞 ${sup.phone || 's/d'} ${sup.email ? `• ✉️ ${sup.email}` : ''}
            </div>
          </div>

          <a href="${waLink}" target="_blank" class="btn-whatsapp" style="white-space: nowrap;">
            💬 WhatsApp
          </a>
        </div>

        ${articles.length > 0 ? `
          <hr style="margin: 10px 0 8px 0; border: none; border-top: 1px solid #EEE;">
          <div style="font-size: 11px; font-weight: bold; color: var(--primary-color); margin-bottom: 6px;">
            📦 Artículos Cotizados (${articles.length}):
          </div>
          <div style="display: flex; flex-direction: column; gap: 6px;">
            ${articles.map(art => `
              <div style="background: #F9F9F9; padding: 8px; border-radius: 6px; border-left: 3px solid var(--accent-color);">
                <div style="display: flex; justify-content: space-between; font-size: 11.5px;">
                  <strong style="color: var(--primary-color);">${art.name || art.description || 'Artículo'}</strong>
                  <span style="color: var(--secondary-color); font-weight: bold;">FOB: $${(parseFloat(art.fob) || 0).toFixed(2)} USD</span>
                </div>
                <div style="font-size: 10px; color: var(--text-muted); margin-top: 2px;">
                  MOQ: <strong>${art.moq || 's/d'} u.</strong> • Puerto: <strong>${art.port || 'China'}</strong> ${art.leadTime ? `• Lead Time: ${art.leadTime}` : ''}
                </div>
                ${art.note ? `<div style="font-size: 10px; color: #444; margin-top: 4px; font-style: italic;">📝 "${art.note}"</div>` : ''}
                
                ${art.photos && art.photos.length > 0 ? `
                  <div style="display: flex; gap: 4px; overflow-x: auto; margin-top: 6px;">
                    ${art.photos.map(p => `<img src="${p}" style="width: 55px; height: 55px; object-fit: cover; border-radius: 4px; border: 1px solid #DDD;">`).join('')}
                  </div>
                ` : ''}

                ${art.voiceNoteUrl ? `
                  <div style="margin-top: 6px;">
                    <div style="font-size: 9.5px; color: var(--secondary-color); font-weight: bold;">🎙️ Nota de Voz:</div>
                    <audio src="${art.voiceNoteUrl}" controls style="width: 100%; height: 30px; margin-top: 2px;"></audio>
                  </div>
                ` : ''}
              </div>
            `).join('')}
          </div>
        ` : ''}
      </div>
    `;
  }).join('');
}

// ESTADO TEMPORAL PARA ALTA DE PROVEEDOR Y ARTÍCULOS COTIZADOS
let currentSupplierArticles = [];
let currentArticlePhotos = [];
let voiceMediaRecorder = null;
let voiceAudioChunks = [];
let voiceAudioBase64 = null;
let voiceRecordTimerInterval = null;
let voiceRecordSeconds = 0;

// ESCÁNER OCR DE TARJETAS DE NEGOCIO Y CARTELES DE STAND
function triggerSupplierCardOcr() {
  const input = document.getElementById('supplierOcrFileInput');
  if (input) input.click();
}

function processSupplierCardOcr(event) {
  const file = event.target.files[0];
  if (!file) return;

  const preview = document.getElementById('supplierOcrPreview');
  const img = document.getElementById('imgSupplierCard');
  const status = document.getElementById('supplierOcrStatus');

  if (preview && img) {
    preview.style.display = 'block';
    img.src = URL.createObjectURL(file);
  }

  if (status) {
    status.textContent = '⏳ Escaneando tarjeta con OCR (Español / Inglés / Chino)... Por favor aguarda...';
  }

  if (typeof Tesseract !== 'undefined') {
    Tesseract.recognize(file, 'eng+chi_sim+spa', {
      logger: m => {
        if (m.status === 'recognizing text' && status) {
          status.textContent = `⏳ Escaneando tarjeta: ${Math.round(m.progress * 100)}%`;
        }
      }
    }).then(({ data: { text } }) => {
      if (status) status.textContent = '✅ Datos extraídos de la tarjeta correctamente!';

      // Extraer email
      const emailMatch = text.match(/\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/);
      if (emailMatch) document.getElementById('pEmail').value = emailMatch[0];

      // Extraer teléfono / WhatsApp
      const phoneMatch = text.match(/(\+?86[- ]?)?1[3-9]\d{9}|\+?\d{1,4}[- ]?\d{3,4}[- ]?\d{4,}/);
      if (phoneMatch) document.getElementById('pPhone').value = phoneMatch[0];

      // Extraer WeChat
      const waMatch = text.match(/(?:wechat|wx|id)[:\s]*([a-zA-Z0-9_-]{5,20})/i);
      if (waMatch) document.getElementById('pWeChat').value = waMatch[1];

      // Extraer Stand / Hall
      const standMatch = text.match(/(?:hall\s*\d+[\.\d]*[a-zA-Z0-9\s-]*|stand\s*[\w\d-]+|booth\s*[\w\d-]+)/i);
      if (standMatch) document.getElementById('pStand').value = standMatch[0];

      // Extraer Caracteres Chinos
      const chineseChars = text.match(/[\u4e00-\u9fa5]{2,10}/g);
      if (chineseChars && chineseChars.length > 0) {
        document.getElementById('pCompanyChinese').value = chineseChars.join(' ');
      }

      // Líneas de texto para Nombre y Contacto
      const lines = text.split('\n').map(l => l.trim()).filter(l => l.length > 2);
      if (lines.length > 0 && !document.getElementById('pCompany').value) {
        document.getElementById('pCompany').value = lines[0];
      }
      if (lines.length > 1 && !document.getElementById('pContact').value) {
        document.getElementById('pContact').value = lines[1];
      }

      // Guardar respaldo en IndexedDB
      const reader = new FileReader();
      reader.onload = (e) => {
        if (db) {
          try {
            const tx = db.transaction('photos', 'readwrite');
            tx.objectStore('photos').add({ image: e.target.result, date: new Date().toISOString(), text, type: 'business_card' });
          } catch (_) {}
        }
      };
      reader.readAsDataURL(file);

    }).catch(err => {
      if (status) status.textContent = '❌ Error en OCR: ' + err.message;
    });
  } else {
    if (status) status.textContent = '⚠️ Tesseract.js no disponible. Ingrese los datos manualmente.';
  }
}

// MODAL DE ALTA DE ARTÍCULOS PARA PROVEEDOR
function openAddArticleModal() {
  document.getElementById('mArtCode').value = 'CF26-ART-' + Math.floor(100 + Math.random() * 900);
  document.getElementById('mArtName').value = '';
  document.getElementById('mArtFob').value = '';
  document.getElementById('mArtMoq').value = '';
  document.getElementById('mArtPort').value = 'Foshan, China';
  document.getElementById('mArtLeadTime').value = '30 días';
  document.getElementById('mArtNote').value = '';
  
  currentArticlePhotos = [];
  document.getElementById('mArtPhotosPreview').innerHTML = '';
  
  voiceAudioBase64 = null;
  voiceAudioChunks = [];
  document.getElementById('audioPreviewContainer').style.display = 'none';
  document.getElementById('voiceRecordTimer').textContent = '';
  document.getElementById('btnRecordVoice').textContent = '🎙️ Grabar Audio';

  document.getElementById('modalAddArticle').classList.add('open');
}

function closeAddArticleModal() {
  if (voiceMediaRecorder && voiceMediaRecorder.state === 'recording') {
    voiceMediaRecorder.stop();
  }
  clearInterval(voiceRecordTimerInterval);
  document.getElementById('modalAddArticle').classList.remove('open');
}

function handleArticlePhotosUpload(event) {
  const files = Array.from(event.target.files);
  if (!files || files.length === 0) return;

  files.forEach(file => {
    const reader = new FileReader();
    reader.onload = (e) => {
      currentArticlePhotos.push(e.target.result);
      renderArticlePhotosPreview();
    };
    reader.readAsDataURL(file);
  });
}

function removeArticlePhoto(index) {
  currentArticlePhotos.splice(index, 1);
  renderArticlePhotosPreview();
}

function renderArticlePhotosPreview() {
  const previewContainer = document.getElementById('mArtPhotosPreview');
  if (!previewContainer) return;
  previewContainer.innerHTML = currentArticlePhotos.map((imgSrc, idx) => `
    <div style="position: relative; display: inline-block;">
      <img src="${imgSrc}" style="width: 70px; height: 70px; object-fit: cover; border-radius: 6px; border: 1px solid #CCC;">
      <button onclick="removeArticlePhoto(${idx})" style="position: absolute; top: -4px; right: -4px; background: #D32F2F; color: white; border: none; border-radius: 50%; width: 18px; height: 18px; font-size: 10px; cursor: pointer; display: flex; align-items: center; justify-content: center;">✖</button>
    </div>
  `).join('');
}

function toggleVoiceRecording() {
  if (voiceMediaRecorder && voiceMediaRecorder.state === 'recording') {
    stopVoiceRecording();
  } else {
    startVoiceRecording();
  }
}

function startVoiceRecording() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    alert('Tu navegador o dispositivo no permite grabar audio.');
    return;
  }

  voiceAudioChunks = [];
  navigator.mediaDevices.getUserMedia({ audio: true })
    .then(stream => {
      voiceMediaRecorder = new MediaRecorder(stream);
      voiceMediaRecorder.start();
      
      voiceRecordSeconds = 0;
      const btn = document.getElementById('btnRecordVoice');
      const timer = document.getElementById('voiceRecordTimer');
      btn.textContent = '⏹️ Detener Grabación';
      btn.style.background = '#D32F2F';

      voiceRecordTimerInterval = setInterval(() => {
        voiceRecordSeconds++;
        timer.textContent = `🔴 ${voiceRecordSeconds}s`;
      }, 1000);

      voiceMediaRecorder.ondataavailable = e => {
        if (e.data.size > 0) {
          voiceAudioChunks.push(e.data);
        }
      };

      voiceMediaRecorder.onstop = () => {
        clearInterval(voiceRecordTimerInterval);
        btn.textContent = '🎙️ Grabar Audio';
        btn.style.background = 'var(--primary-color)';
        timer.textContent = '✅ Audio Grabado';

        const audioBlob = new Blob(voiceAudioChunks, { type: 'audio/webm' });
        const reader = new FileReader();
        reader.onloadend = () => {
          voiceAudioBase64 = reader.result;
          const audioPreview = document.getElementById('audioPreview');
          const container = document.getElementById('audioPreviewContainer');
          if (audioPreview && container) {
            audioPreview.src = voiceAudioBase64;
            container.style.display = 'block';
          }
        };
        reader.readAsDataURL(audioBlob);

        stream.getTracks().forEach(track => track.stop());
      };
    })
    .catch(err => {
      alert('Permiso de micrófono denegado: ' + err.message);
    });
}

function saveInlineArticle() {
  const name = document.getElementById('inlineArtName')?.value || document.getElementById('mArtName')?.value;
  let code = document.getElementById('inlineArtCode')?.value || document.getElementById('mArtCode')?.value;
  if (!code) code = 'CF26-ART-' + Math.floor(100 + Math.random() * 900);
  const fob = parseFloat(document.getElementById('inlineArtFob')?.value || document.getElementById('mArtFob')?.value) || 0.0;
  const moq = parseInt(document.getElementById('inlineArtMoq')?.value || document.getElementById('mArtMoq')?.value) || 0;
  const port = document.getElementById('inlineArtPort')?.value || document.getElementById('mArtPort')?.value || 'Foshan, China';
  const leadTime = document.getElementById('inlineArtLeadTime')?.value || document.getElementById('mArtLeadTime')?.value || '30 días';
  const note = document.getElementById('inlineArtNote')?.value || document.getElementById('mArtNote')?.value || '';

  if (!name) {
    alert('Por favor ingresa el nombre del producto');
    return;
  }

  const newArticle = {
    id: 'ART-' + Date.now(),
    code: code,
    name: name,
    fob: fob,
    moq: moq,
    port: port,
    leadTime: leadTime,
    note: note,
    photos: [...currentArticlePhotos],
    voiceNoteUrl: voiceAudioBase64 || null,
    createdAt: new Date().toISOString()
  };

  currentSupplierArticles.push(newArticle);

  savedArticles.unshift({
    code: newArticle.code,
    name: newArticle.name,
    supplier: document.getElementById('pCompany').value || 'Proveedor Feria',
    fob: newArticle.fob,
    moq: newArticle.moq,
    port: newArticle.port,
    weightKg: 5.0
  });

  // Limpiar campos del formulario inline
  if (document.getElementById('inlineArtName')) document.getElementById('inlineArtName').value = '';
  if (document.getElementById('inlineArtCode')) document.getElementById('inlineArtCode').value = '';
  if (document.getElementById('inlineArtFob')) document.getElementById('inlineArtFob').value = '';
  if (document.getElementById('inlineArtMoq')) document.getElementById('inlineArtMoq').value = '';
  if (document.getElementById('inlineArtNote')) document.getElementById('inlineArtNote').value = '';
  
  currentArticlePhotos = [];
  renderArticlePhotosPreview();
  voiceAudioBase64 = null;
  voiceAudioChunks = [];
  if (document.getElementById('audioPreviewContainer')) document.getElementById('audioPreviewContainer').style.display = 'none';

  renderSupplierArticlesList();
  renderTab2List();
}

function saveArticleFromModal() {
  const code = document.getElementById('mArtCode').value || ('CF26-ART-' + Math.floor(100 + Math.random() * 900));
  const name = document.getElementById('mArtName').value;
  const fob = parseFloat(document.getElementById('mArtFob').value) || 0.0;
  const moq = parseInt(document.getElementById('mArtMoq').value) || 0;
  const port = document.getElementById('mArtPort').value || 'Foshan, China';
  const leadTime = document.getElementById('mArtLeadTime').value || '30 días';
  const note = document.getElementById('mArtNote').value || '';

  if (!name) {
    alert('Por favor ingresa el nombre del producto');
    return;
  }

  const newArticle = {
    id: 'ART-' + Date.now(),
    code: code,
    name: name,
    fob: fob,
    moq: moq,
    port: port,
    leadTime: leadTime,
    note: note,
    photos: [...currentArticlePhotos],
    voiceNoteUrl: voiceAudioBase64 || null,
    createdAt: new Date().toISOString()
  };

  currentSupplierArticles.push(newArticle);

  savedArticles.unshift({
    code: newArticle.code,
    name: newArticle.name,
    supplier: document.getElementById('pCompany').value || 'Proveedor Feria',
    fob: newArticle.fob,
    moq: newArticle.moq,
    port: newArticle.port,
    weightKg: 5.0
  });

  renderSupplierArticlesList();
  renderTab2List();
  closeAddArticleModal();
}

function removeSupplierArticle(index) {
  currentSupplierArticles.splice(index, 1);
  renderSupplierArticlesList();
}

function renderSupplierArticlesList() {
  const container = document.getElementById('supplierArticlesListContainer');
  if (!container) return;

  if (currentSupplierArticles.length === 0) {
    container.innerHTML = `<div style="font-size: 11px; color: var(--text-muted);">No hay artículos cargados aún para este proveedor. Haz clic en "➕ Sumar Artículo".</div>`;
    return;
  }

  container.innerHTML = currentSupplierArticles.map((art, idx) => `
    <div class="card" style="border-left: 3px solid var(--accent-color); margin-bottom: 8px; background: #FAFAFA;">
      <div style="display: flex; justify-content: space-between; align-items: flex-start;">
        <div>
          <strong style="font-size: 12px; color: var(--primary-color);">${art.name}</strong>
          <div style="font-size: 10px; color: var(--text-muted);">
            SKU: ${art.code} • Puerto: ${art.port} • Lead Time: ${art.leadTime}
          </div>
          <div style="font-size: 11px; font-weight: bold; color: var(--secondary-color); margin-top: 4px;">
            FOB: $${art.fob.toFixed(2)} USD • MOQ: ${art.moq} u.
          </div>
          ${art.note ? `<div style="font-size: 10px; color: #555; margin-top: 2px;">📝 ${art.note}</div>` : ''}
        </div>
        <button onclick="removeSupplierArticle(${idx})" class="btn-chip" style="color: #D32F2F; border-color: #D32F2F; padding: 2px 6px; font-size: 10px;">Eliminar</button>
      </div>

      ${art.photos && art.photos.length > 0 ? `
        <div style="display: flex; gap: 4px; overflow-x: auto; margin-top: 6px;">
          ${art.photos.map(p => `<img src="${p}" style="width: 50px; height: 50px; object-fit: cover; border-radius: 4px;">`).join('')}
        </div>
      ` : ''}

      ${art.voiceNoteUrl ? `
        <div style="margin-top: 6px;">
          <audio src="${art.voiceNoteUrl}" controls style="width: 100%; height: 30px;"></audio>
        </div>
      ` : ''}
    </div>
  `).join('');

  const lbl = document.getElementById('lblTotalArticles');
  if (lbl) lbl.textContent = savedArticles.length;
}

// ALTA DE PROVEEDORES DESDE FORMULARIO (Sincroniza con Firebase Cloud Firestore)
function saveSupplierFromForm() {
  const name = document.getElementById('pCompany').value;
  const companyChinese = document.getElementById('pCompanyChinese').value;
  const stand = document.getElementById('pStand').value;
  const cat = document.getElementById('pCategory').value;
  const contact = document.getElementById('pContact').value;
  const wechat = document.getElementById('pWeChat').value;
  const phone = document.getElementById('pPhone').value;
  const email = document.getElementById('pEmail').value;

  if (!name) {
    alert('Por favor ingresa el nombre de la empresa');
    return;
  }

  const supplierData = {
    id: 'SUP-' + (localSuppliers.length + 1),
    companyName: name,
    companyNameChinese: companyChinese || '',
    stand: stand || 'Stand s/d',
    category: cat || 'General',
    contactName: contact || 'Contacto',
    weChat: wechat || '',
    phone: phone || '+86',
    email: email || '',
    articles: [...currentSupplierArticles],
    createdAt: new Date().toISOString()
  };

  localSuppliers.unshift(supplierData);

  // Sync with Firebase Cloud Firestore (perrenycia-crm)
  if (fbDb) {
    fbDb.collection('suppliers').add(supplierData)
      .then(() => console.log('🟢 Sync exitosa con Firebase Firestore (perrenycia-crm)'))
      .catch(err => console.log('Firestore sync notice:', err));
  }

  // Limpiar campos del formulario
  document.getElementById('pCompany').value = '';
  document.getElementById('pCompanyChinese').value = '';
  document.getElementById('pStand').value = '';
  document.getElementById('pCategory').value = '';
  document.getElementById('pContact').value = '';
  document.getElementById('pWeChat').value = '';
  document.getElementById('pPhone').value = '';
  document.getElementById('pEmail').value = '';
  document.getElementById('supplierOcrPreview').style.display = 'none';
  document.getElementById('supplierOcrStatus').textContent = '';

  currentSupplierArticles = [];
  renderSupplierArticlesList();

  document.getElementById('lblTotalSuppliers').textContent = localSuppliers.length;
  renderTarjetero();
  alert('✅ Proveedor y sus artículos guardados correctamente en Firebase Cloud y local!');
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

  const query = (document.getElementById('perrenSearchInput')?.value || '').toLowerCase().trim();
  const filtered = perrenSqlDatabase.filter(i => 
    !query || 
    (i.sku && i.sku.toLowerCase().includes(query)) || 
    (i.description && i.description.toLowerCase().includes(query)) || 
    (i.brand && i.brand.toLowerCase().includes(query))
  );

  const display = filtered.slice(0, 30);

  container.innerHTML = display.map(item => `
    <div style="padding: 8px; border-bottom: 1px solid #EEE; display: flex; justify-content: space-between; align-items: center;">
      <div>
        <strong style="font-size: 11px; color: var(--primary-color);">${item.description}</strong>
        <div style="font-size: 9.5px; color: var(--text-muted);">${item.sku} • ${item.brand} • $${item.costNoVAT.toLocaleString('es-AR')} ARS</div>
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

function getAllCantonArticles() {
  const list = [];
  savedArticles.forEach((sa, idx) => {
    list.push({ ...sa, source: 'saved', index: idx });
  });
  localSuppliers.forEach(sup => {
    (sup.articles || []).forEach((art, aIdx) => {
      list.push({
        code: art.code || ('ART-CN-' + aIdx),
        name: art.name || art.description || 'Artículo Cotizado',
        supplier: sup.companyName || 'Proveedor Cantón',
        fob: parseFloat(art.fob) || 0,
        moq: parseInt(art.moq) || 1,
        port: art.port || 'Foshan, China',
        weightKg: parseFloat(art.weightKg) || 3.2,
        source: 'supplier'
      });
    });
  });
  return list;
}

function openCantonSelectModal() {
  const modal = document.getElementById('modalCantonSelect');
  if (modal) {
    modal.classList.add('open');
    renderCantonSelectResults();
  }
}

function closeCantonSelectModal() {
  const modal = document.getElementById('modalCantonSelect');
  if (modal) modal.classList.remove('open');
}

function renderCantonSelectResults() {
  const container = document.getElementById('cantonSelectSearchResults');
  if (!container) return;

  const query = (document.getElementById('cantonSelectSearchInput')?.value || '').toLowerCase().trim();
  const allArticles = getAllCantonArticles();

  const filtered = allArticles.filter(art => 
    !query || 
    (art.name && art.name.toLowerCase().includes(query)) ||
    (art.code && art.code.toLowerCase().includes(query)) ||
    (art.supplier && art.supplier.toLowerCase().includes(query)) ||
    (art.port && art.port.toLowerCase().includes(query))
  );

  if (filtered.length === 0) {
    container.innerHTML = `<div style="text-align: center; font-size: 11px; color: var(--text-muted); padding: 15px;">No hay artículos cotizados en Cantón aún. Puedes cargar más en la pestaña <strong>"+ Proveedor"</strong>.</div>`;
    return;
  }

  container.innerHTML = filtered.map((item, idx) => `
    <div style="padding: 10px; border-bottom: 1px solid #EEE; display: flex; justify-content: space-between; align-items: center;">
      <div>
        <strong style="font-size: 11.5px; color: var(--primary-color);">${item.name}</strong>
        <div style="font-size: 10px; color: var(--text-muted); margin-top: 2px;">
          Fábrica: <strong>${item.supplier}</strong> • FOB: <strong>$${item.fob.toFixed(2)} USD</strong> • MOQ: ${item.moq} u. • Puerto: ${item.port}
        </div>
      </div>
      <button class="btn-chip" onclick="selectCantonItemFromIndex(${idx})" style="background: var(--accent-color); color: white; border: none; padding: 4px 8px;">Seleccionar</button>
    </div>
  `).join('');
}

function selectCantonItemFromIndex(index) {
  const allArticles = getAllCantonArticles();
  if (allArticles[index]) {
    selectedCantonItem = allArticles[index];
    renderSelectedCantonCard();
    renderComparisonResult();
  }
  closeCantonSelectModal();
}

function renderSelectedCantonCard() {
  const container = document.getElementById('selectedCantonCard');
  if (!container) return;

  if (!selectedCantonItem) {
    container.innerHTML = `
      <div style="font-size: 11px; color: var(--text-muted); font-style: italic;">
        Haz clic en "📦 Elegir Artículo Cotizado" para seleccionar un producto de Cantón.
      </div>
    `;
    return;
  }

  container.innerHTML = `
    <div style="background-color: #E8F5E9; padding: 10px; border-radius: 8px; border-left: 4px solid var(--accent-color);">
      <div style="font-size: 10px; color: var(--accent-color); font-weight: bold;">🇨🇳 ARTÍCULO SELECCIONADO CANTÓN (CHINA):</div>
      <strong style="font-size: 12px; color: var(--primary-color);">${selectedCantonItem.name}</strong>
      <div style="font-size: 10.5px; margin-top: 4px;">
        Fábrica: <strong>${selectedCantonItem.supplier || 'Proveedor Cantón'}</strong> • FOB: <strong>$${selectedCantonItem.fob.toFixed(2)} USD</strong> • MOQ: <strong>${selectedCantonItem.moq} u.</strong> • Puerto: <strong>${selectedCantonItem.port || 'China'}</strong>
      </div>
    </div>
  `;
}

function renderSelectedPerrenCard() {
  const container = document.getElementById('selectedPerrenCard');
  if (!container) return;

  if (!selectedPerrenItem) {
    container.innerHTML = `
      <div style="font-size: 11px; color: var(--text-muted); font-style: italic;">
        Haz clic en "📂 Buscar en Base SQL" para elegir un artículo de Perren.
      </div>
    `;
    return;
  }

  const costUSD = selectedPerrenItem.costNoVAT / stateSettings.tc;

  container.innerHTML = `
    <div style="background-color: #FFEBEE; padding: 10px; border-radius: 8px; border-left: 4px solid #D32F2F;">
      <div style="font-size: 10px; color: #D32F2F; font-weight: bold;">🇦🇷 ARTÍCULO SELECCIONADO PERREN FLEXXUS (ARGENTINA):</div>
      <strong style="font-size: 12px; color: var(--primary-color);">${selectedPerrenItem.description}</strong>
      <div style="font-size: 10.5px; margin-top: 4px;">
        SKU: <strong>${selectedPerrenItem.sku}</strong> • Marca: <strong>${selectedPerrenItem.brand || 'Varios'}</strong> • Costo Neto: <strong>$${selectedPerrenItem.costNoVAT.toLocaleString('es-AR')} ARS</strong> (${formatMoney(costUSD)})
      </div>
    </div>
  `;
}

function renderComparisonResult() {
  const container = document.getElementById('comparisonResultCard');
  if (!container) return;

  if (!selectedPerrenItem && !selectedCantonItem) {
    container.style.display = 'none';
    return;
  }

  container.style.display = 'block';

  if (!selectedPerrenItem || !selectedCantonItem) {
    container.innerHTML = `
      <div style="text-align: center; font-size: 11px; color: var(--text-muted); padding: 12px;">
        💡 Selecciona ambos artículos arriba (1 de Perren Argentina y 1 de Cantón China) para ver el desglose transparente de costos y el ahorro directo.
      </div>
    `;
    return;
  }

  // Cálculo completo transparente de costos de Cantón (China)
  const cantonBreakdown = calculateLandedBreakdown(
    selectedCantonItem.fob, 
    selectedCantonItem.moq || 2000, 
    selectedCantonItem.weightKg || 3.2
  );

  const cantonLandedUnitUSD = cantonBreakdown.costoIncididoUnit;
  const perrenCostUSD = selectedPerrenItem.costNoVAT / stateSettings.tc;
  const perrenCostARS = selectedPerrenItem.costNoVAT;

  const diffUSD = perrenCostUSD - cantonLandedUnitUSD;
  const diffPct = perrenCostUSD > 0 ? ((diffUSD / perrenCostUSD) * 100).toFixed(1) : '0';
  const totalShipmentQty = selectedCantonItem.moq || 1;
  const totalProfitUSD = diffUSD * totalShipmentQty;

  container.innerHTML = `
    <h3 style="font-size: 13px; color: var(--primary-color); margin-bottom: 10px;">⚡ ANÁLISIS COMPARATIVO DETALLADO DE COSTOS</h3>

    <!-- Resumen Comparativo de Costos Unitarios -->
    <div class="row-2" style="margin-bottom: 12px;">
      <div style="background: #FFEBEE; padding: 10px; border-radius: 8px; border-left: 4px solid #D32F2F;">
        <strong style="font-size: 11px; color: #D32F2F;">🇦🇷 COSTO NETO PERREN (ARGENTINA):</strong>
        <div style="font-size: 16px; font-weight: bold; color: #D32F2F; margin-top: 2px;">
          ${formatMoney(perrenCostUSD)}
        </div>
        <div style="font-size: 9.5px; color: #666;">
          $${perrenCostARS.toLocaleString('es-AR', { minimumFractionDigits: 2 })} ARS (Sin IVA)
        </div>
      </div>

      <div style="background: #E8F5E9; padding: 10px; border-radius: 8px; border-left: 4px solid var(--accent-color);">
        <strong style="font-size: 11px; color: var(--accent-color);">🇨🇳 COSTO LANDED CANTÓN (CHINA):</strong>
        <div style="font-size: 16px; font-weight: bold; color: var(--accent-color); margin-top: 2px;">
          ${formatMoney(cantonLandedUnitUSD)}
        </div>
        <div style="font-size: 9.5px; color: #666;">
          Puesto en Depósito (Incluye Flete, Seguro, Arancel y Despacho)
        </div>
      </div>
    </div>

    <!-- Indicador de Ahorro y Ganancia -->
    <div style="background: linear-gradient(135deg, #1565C0 0%, #0D47A1 100%); color: white; padding: 12px; border-radius: 8px; text-align: center; margin-bottom: 15px;">
      <div style="font-size: 11px; opacity: 0.9; text-transform: uppercase; letter-spacing: 0.5px;">🎯 AHORRO DIRECTO ESTIMADO:</div>
      <div style="font-size: 24px; font-weight: bold; margin: 4px 0; color: #69F0AE;">
        +${diffPct}% (${formatMoney(diffUSD)} / unidad)
      </div>
      <div style="font-size: 11px; opacity: 0.95;">
        Ganancia estimada por embarque de <strong>${totalShipmentQty.toLocaleString()} u.</strong>: 
        <strong style="color: #FFD54F; font-size: 13px;">${formatMoney(totalProfitUSD)}</strong>
      </div>
    </div>

    <!-- Desglose Paso a Paso de Costos de China (Explicación transparente) -->
    <div style="margin-top: 10px;">
      <h4 style="font-size: 12px; color: var(--primary-color); margin-bottom: 4px;">
        📊 DESGLOSE PASO A PASO CÓMO SE CALCULA EL COSTO LANDED DE CANTÓN:
      </h4>
      <p style="font-size: 10px; color: var(--text-muted); margin-bottom: 8px;">
        Cálculo basado en FOB $${selectedCantonItem.fob.toFixed(2)} USD, MOQ ${totalShipmentQty} u., peso ${(selectedCantonItem.weightKg || 3.2)} kg y Dólar TC $${stateSettings.tc.toLocaleString('es-AR')} ARS:
      </p>

      <table class="breakdown-table" style="width: 100%;">
        <thead>
          <tr>
            <th>CONCEPTO TRIBUTARIO / LOGÍSTICO</th>
            <th>%</th>
            <th style="text-align: right;">UNITARIO</th>
            <th style="text-align: right;">TOTAL EMBARQUE (${totalShipmentQty} u.)</th>
          </tr>
        </thead>
        <tbody>
          ${cantonBreakdown.rows.map(r => {
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
          }).join('')}
        </tbody>
      </table>
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

    const reader = new FileReader();
    reader.onload = (e) => {
      if (db) {
        const tx = db.transaction('photos', 'readwrite');
        tx.objectStore('photos').add({ image: e.target.result, date: new Date().toISOString(), text });
      }
    };
    reader.readAsDataURL(file);

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
