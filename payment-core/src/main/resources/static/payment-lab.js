// LAB ONLY - NEVER ENABLE FOR REAL PAYMENT PROVIDERS.
// Interfaz vanilla del laboratorio QR. La fuente de verdad es el backend:
// este script jamás calcula estados ni fabrica pagos, solo llama a la API.
'use strict';

const state = { paymentId: null, eventSource: null, pollTimer: null };

const TERMINAL = ['PAID', 'FAILED', 'EXPIRED', 'CANCELLED'];

const STATUS_TEXT = {
  PENDING: 'Esperando pago...',
  PAID: 'Pago confirmado',
  FAILED: 'Pago fallido',
  EXPIRED: 'Pago expirado',
  CANCELLED: 'Pago cancelado',
};

/**
 * Convierte "7.00" a minor units (700) solo con String/enteros.
 * Acepta "5", "5.00", "7.50", "10.99". Rechaza 0, negativos,
 * más de 2 decimales y texto inválido. Sin aritmética float.
 */
function parseMoneyToMinorUnits(text) {
  const raw = String(text).trim();
  if (!/^\d+(\.\d{1,2})?$/.test(raw)) {
    throw new Error('Monto inválido: use hasta 2 decimales (ej. 7.00)');
  }
  const parts = raw.split('.');
  const soles = parts[0];
  const cents = (parts[1] || '').padEnd(2, '0');
  const minor = Number(soles) * 100 + Number(cents);
  if (!Number.isSafeInteger(minor) || minor <= 0) {
    throw new Error('Monto debe ser mayor que 0');
  }
  return minor;
}

function showError(message) {
  const el = document.getElementById('form-error');
  el.textContent = message;
  el.hidden = false;
}

function clearError() {
  document.getElementById('form-error').hidden = true;
}

function setConnection(online) {
  const el = document.getElementById('conn-indicator');
  el.textContent = online ? 'CONNECTED' : 'DISCONNECTED';
  el.classList.toggle('on', online);
}

function renderPayment(payment) {
  const isYape = payment.method === 'YAPE_NOTIFICATION';
  document.getElementById('qr-img').hidden = isYape;
  document.getElementById('yape-panel').hidden = !isYape;
  document.getElementById('status-pill').textContent = payment.status;
  document.getElementById('status-pill').classList.toggle('bad', payment.status !== 'PAID' && payment.status !== 'PENDING');
  document.getElementById('status-text').textContent = isYape && payment.status === 'PENDING'
    ? 'Esperando Yape...'
    : (STATUS_TEXT[payment.status] || payment.status);
  document.getElementById('d-id').textContent = payment.id;
  document.getElementById('d-reference').textContent = payment.reference;
  document.getElementById('d-amount').textContent = payment.amountDisplay;
  document.getElementById('d-paidat').textContent = payment.paidAt || '-';
  document.getElementById('d-opref').textContent = payment.providerOperationReference || '-';
  document.getElementById('qr-img').classList.toggle('dim', payment.status === 'PAID');
  if (isYape) {
    document.getElementById('yape-total').textContent = payment.amountDisplay;
    const staticImg = document.getElementById('yape-static-qr');
    if (payment.staticQrUrl) {
      staticImg.src = payment.staticQrUrl;
      staticImg.hidden = false;
    } else {
      staticImg.removeAttribute('src');
      staticImg.hidden = true;
    }
    document.getElementById('yape-ambiguous').hidden = true;
  }
  if (payment.status === 'PAID') {
    document.getElementById('status-text').textContent = '✓ ' + STATUS_TEXT.PAID;
  }
}

async function api(path, options) {
  const response = await fetch(path, options);
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body.message || ('HTTP ' + response.status));
  }
  return body;
}

async function refreshDetails() {
  if (!state.paymentId) return;
  const payment = await api('/api/payments/' + encodeURIComponent(state.paymentId));
  renderPayment(payment);
}

function closeEvents() {
  if (state.eventSource) {
    state.eventSource.close();
    state.eventSource = null;
  }
  if (state.pollTimer) {
    clearInterval(state.pollTimer);
    state.pollTimer = null;
  }
  setConnection(false);
}

/**
 * LAB ONLY: sondea eventos AMBIGUOUS del bridge para avisar al cajero
 * que debe pedir el código de seguridad. Solo laboratorio.
 */
function pollAmbiguous(payment) {
  if (state.pollTimer) return;
  state.pollTimer = setInterval(async () => {
    try {
      const events = await api('/api/lab/bridge/events');
      const hit = events.find((e) => e.matchStatus === 'AMBIGUOUS'
        && e.amountMinor === payment.amountMinor);
      if (hit) {
        document.getElementById('yape-ambiguous').hidden = false;
      }
    } catch (_) {
      // Solo laboratorio: el sondeo nunca rompe la vista principal.
    }
  }, 3000);
}

function connectEvents(paymentId) {
  closeEvents();
  const source = new EventSource('/api/payments/' + encodeURIComponent(paymentId) + '/events');
  state.eventSource = source;
  source.onopen = () => setConnection(true);
  source.onerror = () => setConnection(false);
  source.addEventListener('payment-status', (event) => {
    const message = JSON.parse(event.data);
    if (TERMINAL.includes(message.status)) {
      // El evento trae solo status: pedir detalles finales sin recargar.
      refreshDetails().catch((err) => showError(err.message));
      closeEvents();
    } else {
      renderPayment(Object.assign({}, window.__lastPayment || {}, { status: message.status }));
    }
  });
}

async function createPayment() {
  clearError();
  const button = document.getElementById('create-btn');
  let minor;
  try {
    minor = parseMoneyToMinorUnits(document.getElementById('amount').value);
  } catch (err) {
    showError(err.message);
    return;
  }
  const reference = document.getElementById('reference').value.trim();
  if (!reference) {
    showError('Reference no debe estar vacía');
    return;
  }
  button.disabled = true;
  try {
    const methodEl = document.querySelector('input[name="paymethod"]:checked');
    const method = methodEl ? methodEl.value : 'SYNTHETIC_QR';
    const payment = await api('/api/payments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reference, amountMinor: minor, currency: '604', method }),
    });
    // Idempotencia visual: se muestra lo que devuelva el backend, sin lógica local.
    state.paymentId = payment.id;
    window.__lastPayment = payment;
    if (payment.method === 'YAPE_NOTIFICATION') {
      document.getElementById('qr-img').removeAttribute('src');
      pollAmbiguous(payment);
    } else {
      document.getElementById('qr-img').src = '/api/payments/' + encodeURIComponent(payment.id) + '/qr';
    }
    document.getElementById('create-section').hidden = true;
    document.getElementById('payment-section').hidden = false;
    document.getElementById('lab-controls').hidden = false;
    renderPayment(payment);
    connectEvents(payment.id);
  } catch (err) {
    showError(err.message);
  } finally {
    button.disabled = false;
  }
}

async function labAction(action) {
  const errorEl = document.getElementById('lab-error');
  errorEl.hidden = true;
  if (!state.paymentId) return;
  try {
    const payment = await api('/api/lab/payments/' + encodeURIComponent(state.paymentId) + '/' + action, { method: 'POST' });
    window.__lastPayment = payment;
    renderPayment(payment);
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.hidden = false;
  }
}

function newPayment() {
  closeEvents();
  state.paymentId = null;
  window.__lastPayment = null;
  document.getElementById('qr-img').removeAttribute('src');
  document.getElementById('yape-static-qr').removeAttribute('src');
  document.getElementById('yape-code').value = '';
  document.getElementById('yape-error').hidden = true;
  document.getElementById('yape-ambiguous').hidden = true;
  document.getElementById('payment-section').hidden = true;
  document.getElementById('lab-controls').hidden = true;
  document.getElementById('create-section').hidden = false;
  clearError();
}

async function resolveYape() {
  const errorEl = document.getElementById('yape-error');
  errorEl.hidden = true;
  if (!state.paymentId) return;
  const code = document.getElementById('yape-code').value.trim();
  // El código esperado nunca se muestra: solo se envía al backend.
  try {
    const payment = await api('/api/payments/' + encodeURIComponent(state.paymentId) + '/resolve-yape', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ securityCode: code }),
    });
    window.__lastPayment = payment;
    renderPayment(payment);
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.hidden = false;
  }
}

document.getElementById('yape-resolve').addEventListener('click', resolveYape);

document.getElementById('create-btn').addEventListener('click', createPayment);
document.getElementById('new-btn').addEventListener('click', newPayment);
document.getElementById('lab-confirm').addEventListener('click', () => labAction('confirm'));
document.getElementById('lab-fail').addEventListener('click', () => labAction('fail'));
document.getElementById('lab-expire').addEventListener('click', () => labAction('expire'));
document.getElementById('lab-cancel').addEventListener('click', async () => {
  const errorEl = document.getElementById('lab-error');
  errorEl.hidden = true;
  if (!state.paymentId) return;
  try {
    const payment = await api('/api/payments/' + encodeURIComponent(state.paymentId) + '/cancel', { method: 'POST' });
    window.__lastPayment = payment;
    renderPayment(payment);
  } catch (err) {
    errorEl.textContent = err.message;
    errorEl.hidden = false;
  }
});
