/**
 * AutoTag Docs — Frontend SPA Application
 * Connects to Spring Boot 25 backend, displays real-time triggers, vector search & tags
 */

const API_BASE = (window.location.port === '8081' || window.location.port === '') ? '' : 'http://localhost:8081';

// App State
let state = {
    documents: [],
    selectedTag: '',
    selectedStatus: '',
    pollingInterval: null,
    isUploading: false
};

// DOM Elements
const docTbody = document.getElementById('documents-tbody');
const statTotal = document.getElementById('stat-total');
const statProcessing = document.getElementById('stat-processing');
const statReady = document.getElementById('stat-ready');
const statTags = document.getElementById('stat-tags');
const tagFilterChips = document.getElementById('tag-filter-chips');
const filterStatus = document.getElementById('filter-status');
const dropzone = document.getElementById('dropzone');
const fileInput = document.getElementById('file-input');
const uploadProgress = document.getElementById('upload-progress');
const progressBarFill = document.getElementById('progress-bar-fill');
const progressStatusText = document.getElementById('progress-status-text');
const progressPercentage = document.getElementById('progress-percentage');
const searchInput = document.getElementById('input-semantic-search');
const btnSearch = document.getElementById('btn-do-search');
const searchResultsList = document.getElementById('search-results-list');
const terminalScreen = document.getElementById('terminal-screen');
const themeToggle = document.getElementById('theme-toggle');

// Initialize on Load
document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    initTabs();
    initUploadListeners();
    initFilters();
    initSearch();
    initDiagnostics();
    initModal();

    // Initial Data Fetch
    fetchDocuments();
    checkHealth();

    logTerminal('Panel AutoTag Docs iniciado. Conectado a ' + API_BASE, 'info');
});

/* ==========================================================================
   1. Theme Management (Light / Dark Mode)
   ========================================================================== */
function initModal() {
    const modal = document.getElementById('doc-modal');
    const btnClose = document.getElementById('modal-btn-close');
    if (btnClose) btnClose.addEventListener('click', closeDocModal);
    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeDocModal();
        });
    }
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeDocModal();
    });
}

function initTheme() {
    const savedTheme = localStorage.getItem('autotag_theme') || 'dark';
    document.documentElement.setAttribute('data-theme', savedTheme);

    if (themeToggle) {
        themeToggle.addEventListener('click', () => {
            const currentTheme = document.documentElement.getAttribute('data-theme');
            const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
            document.documentElement.setAttribute('data-theme', newTheme);
            localStorage.setItem('autotag_theme', newTheme);
            showToast(`Tema cambiado a modo ${newTheme === 'dark' ? 'oscuro' : 'claro'}`, 'info');
        });
    }
}

/* ==========================================================================
   2. Tab Navigation
   ========================================================================== */
function initTabs() {
    const navButtons = document.querySelectorAll('.nav-btn');
    const tabViews = document.querySelectorAll('.tab-view');
    const pageTitle = document.getElementById('page-title');
    const pageSubtitle = document.getElementById('page-subtitle');

    const titles = {
        'dashboard': {
            title: 'Gestión de Documentos & Ingesta',
            sub: 'Extracción RAG, chunking y etiquetado vectorial en tiempo real'
        },
        'search': {
            title: 'Búsqueda Semántica Vectorial',
            sub: 'Recuperación de fragmentos mediante similitud de coseno en Qdrant (384d)'
        },
        'triggers': {
            title: 'Monitor de Triggers & Webhooks',
            sub: 'Ciclo de vida asíncrono entre Spring Boot, n8n y Qdrant'
        }
    };

    navButtons.forEach(btn => {
        btn.addEventListener('click', () => {
            const tab = btn.dataset.tab;

            navButtons.forEach(b => b.classList.remove('active'));
            tabViews.forEach(v => v.classList.remove('active'));

            btn.classList.add('active');
            const targetView = document.getElementById(`view-${tab}`);
            if (targetView) targetView.classList.add('active');

            if (titles[tab]) {
                pageTitle.textContent = titles[tab].title;
                pageSubtitle.textContent = titles[tab].sub;
            }
        });
    });

    // Quick upload button in topbar
    const btnQuickUpload = document.getElementById('btn-quick-upload');
    if (btnQuickUpload) {
        btnQuickUpload.addEventListener('click', () => {
            // switch to dashboard first if not active
            const dashBtn = document.querySelector('.nav-btn[data-tab="dashboard"]');
            if (dashBtn) dashBtn.click();
            fileInput.click();
        });
    }

    // Refresh list button in topbar
    const btnRefresh = document.getElementById('btn-refresh-list');
    if (btnRefresh) {
        btnRefresh.addEventListener('click', () => {
            fetchDocuments();
            checkHealth();
            showToast('Documentos actualizados', 'info');
        });
    }
}

/* ==========================================================================
   3. File Upload & Drag and Drop with Progress
   ========================================================================== */
function initUploadListeners() {
    const btnBrowse = document.getElementById('btn-browse-file');

    if (btnBrowse && fileInput) {
        btnBrowse.addEventListener('click', (e) => {
            e.stopPropagation();
            fileInput.click();
        });
    }

    if (dropzone && fileInput) {
        dropzone.addEventListener('click', () => {
            fileInput.click();
        });

        dropzone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropzone.classList.add('dragover');
        });

        dropzone.addEventListener('dragleave', () => {
            dropzone.classList.remove('dragover');
        });

        dropzone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropzone.classList.remove('dragover');
            if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                const file = e.dataTransfer.files[0];
                uploadFile(file);
            }
        });

        fileInput.addEventListener('change', () => {
            if (fileInput.files && fileInput.files.length > 0) {
                const file = fileInput.files[0];
                uploadFile(file);
                fileInput.value = ''; // Reset input
            }
        });
    }
}

async function uploadFile(file) {
    if (!file) return;
    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
        showToast('Solo se permiten archivos en formato PDF.', 'error');
        return;
    }

    state.isUploading = true;
    showUploadProgress(true);
    updateProgress(20, 'Almacenando archivo PDF en backend...');
    logTerminal(`Iniciando subida de archivo: ${file.name} (${(file.size / 1024).toFixed(1)} KB)`, 'info');

    const formData = new FormData();
    formData.append('file', file);

    try {
        updateProgress(50, 'Despachando Webhook asíncrono hacia n8n...');
        
        const response = await fetch(`${API_BASE}/api/documents/upload`, {
            method: 'POST',
            body: formData
        });

        if (!response.ok) {
            throw new Error(`HTTP Error ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();
        updateProgress(100, '¡PDF recibido! Procesando en pipeline IA de n8n...');
        
        logTerminal(`[Trigger 01] Documento registrado en PostgreSQL (ID: ${data.id}, Estado: ${data.status})`, 'success');
        logTerminal(`[Trigger 02] Evento POST /webhook/process-pdf enviado a n8n`, 'info');
        showToast(`Documento subido con éxito: ${file.name}`, 'success');

        setTimeout(() => {
            showUploadProgress(false);
            state.isUploading = false;
        }, 1500);

        // Refresh documents and trigger polling for real-time update
        await fetchDocuments();
        startPolling();

    } catch (err) {
        console.error('Error subiendo archivo:', err);
        showUploadProgress(false);
        state.isUploading = false;
        logTerminal(`Error en subida: ${err.message}`, 'error');
        showToast(`Error al subir: ${err.message}`, 'error');
    }
}

function showUploadProgress(visible) {
    if (uploadProgress) {
        uploadProgress.style.display = visible ? 'block' : 'none';
        if (!visible) {
            progressBarFill.style.width = '0%';
            progressPercentage.textContent = '0%';
        }
    }
}

function updateProgress(percent, statusText) {
    if (progressBarFill) progressBarFill.style.width = `${percent}%`;
    if (progressPercentage) progressPercentage.textContent = `${percent}%`;
    if (progressStatusText && statusText) progressStatusText.textContent = statusText;
}

/* ==========================================================================
   4. Documents Fetching, Stats & Rendering
   ========================================================================== */
async function fetchDocuments() {
    try {
        const response = await fetch(`${API_BASE}/api/documents`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        
        const data = await response.json();
        state.documents = data;
        
        updateMetrics(data);
        renderTagChips(data);
        renderDocumentsTable();

        // Check if any doc is processing
        const hasProcessing = data.some(doc => doc.status === 'PROCESANDO');
        if (hasProcessing) {
            startPolling();
        } else if (state.pollingInterval) {
            stopPolling();
        }

    } catch (err) {
        console.error('Error al obtener documentos:', err);
        docTbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-muted">Error al conectar con el backend (${err.message}). Comprueba que Spring Boot esté corriendo en el puerto 8081.</td></tr>`;
    }
}

function updateMetrics(docs) {
    const total = docs.length;
    const processing = docs.filter(d => d.status === 'PROCESANDO').length;
    const ready = docs.filter(d => d.status === 'LISTO').length;

    // Collect all unique tags
    const allTags = new Set();
    docs.forEach(d => {
        if (Array.isArray(d.tags)) {
            d.tags.forEach(t => allTags.add(t));
        }
    });

    if (statTotal) statTotal.textContent = total;
    if (statProcessing) statProcessing.textContent = processing;
    if (statReady) statReady.textContent = ready;
    if (statTags) statTags.textContent = allTags.size;

    // Solo animar el icono si REALMENTE hay documentos procesando
    const amberIcon = document.querySelector('.metric-amber svg');
    if (amberIcon) {
        if (processing > 0) {
            amberIcon.classList.add('spin-icon');
        } else {
            amberIcon.classList.remove('spin-icon');
        }
    }
}

function renderTagChips(docs) {
    if (!tagFilterChips) return;

    const allTags = new Set();
    docs.forEach(d => {
        if (Array.isArray(d.tags)) {
            d.tags.forEach(t => allTags.add(t));
        }
    });

    let chipsHtml = `<button class="chip ${state.selectedTag === '' ? 'active' : ''}" data-tag="">Todas</button>`;
    allTags.forEach(tag => {
        chipsHtml += `<button class="chip ${state.selectedTag === tag ? 'active' : ''}" data-tag="${escapeHtml(tag)}">${escapeHtml(tag)}</button>`;
    });

    tagFilterChips.innerHTML = chipsHtml;

    // Attach click listeners to chips
    tagFilterChips.querySelectorAll('.chip').forEach(chip => {
        chip.addEventListener('click', () => {
            state.selectedTag = chip.dataset.tag;
            tagFilterChips.querySelectorAll('.chip').forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            renderDocumentsTable();
        });
    });
}

function initFilters() {
    if (filterStatus) {
        filterStatus.addEventListener('change', () => {
            state.selectedStatus = filterStatus.value;
            renderDocumentsTable();
        });
    }
}

function renderDocumentsTable() {
    if (!docTbody) return;

    let filtered = state.documents;

    if (state.selectedTag) {
        filtered = filtered.filter(d => Array.isArray(d.tags) && d.tags.includes(state.selectedTag));
    }

    if (state.selectedStatus) {
        filtered = filtered.filter(d => d.status === state.selectedStatus);
    }

    if (filtered.length === 0) {
        docTbody.innerHTML = `<tr><td colspan="6" class="text-center py-8 text-muted">No se encontraron documentos con los filtros seleccionados.</td></tr>`;
        return;
    }

    docTbody.innerHTML = filtered.map(doc => {
        // Status Badge
        let statusBadge = '';
        if (doc.status === 'LISTO') {
            statusBadge = `<span class="badge-status status-ready"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg> LISTO</span>`;
        } else if (doc.status === 'PROCESANDO') {
            statusBadge = `<span class="badge-status status-processing">PROCESANDO</span>`;
        } else {
            statusBadge = `<span class="badge-status status-failed">FALLIDO</span>`;
        }

        // Tags badges
        let tagsHtml = '<span class="text-muted" style="font-size:0.75rem">—</span>';
        if (Array.isArray(doc.tags) && doc.tags.length > 0) {
            tagsHtml = `<div class="tag-badge-group">` + doc.tags.map(tag => {
                let colorClass = '';
                const lower = tag.toLowerCase();
                if (lower.includes('finanz') || lower.includes('factur')) colorClass = 'alt-finanzas';
                else if (lower.includes('legal') || lower.includes('contrat')) colorClass = 'alt-legal';
                else if (lower.includes('tec') || lower.includes('arquit')) colorClass = 'alt-tec';
                return `<span class="tag-badge ${colorClass}">${escapeHtml(tag)}</span>`;
            }).join('') + `</div>`;
        }

        // Summary & Chunks
        const summaryText = doc.summary ? escapeHtml(doc.summary) : 'Procesando extracción en n8n...';
        const chunksBadge = doc.chunksCount > 0 ? `<div class="chunks-pill">${doc.chunksCount} chunks en Qdrant</div>` : '';

        // Formatted Date
        const dateStr = doc.createdAt ? new Date(doc.createdAt).toLocaleString('es-ES', { dateStyle: 'short', timeStyle: 'short' }) : '—';

        return `
            <tr onclick="showDocModal('${doc.id}')" title="Haz clic para ver detalles y vectores en Qdrant">
                <td>
                    <div class="doc-name-cell">
                        <div class="doc-icon">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>
                        </div>
                        <div>
                            <div>${escapeHtml(doc.fileName)}</div>
                            <div class="doc-id-sub">${doc.id.substring(0, 8)}... • ${(doc.fileSize ? (doc.fileSize / 1024).toFixed(1) : '0')} KB</div>
                        </div>
                    </div>
                </td>
                <td>${statusBadge}</td>
                <td>${tagsHtml}</td>
                <td>
                    <div class="rag-summary-cell">
                        <div>${summaryText}</div>
                        ${chunksBadge}
                    </div>
                </td>
                <td style="white-space: nowrap; font-size: 0.8rem; color: var(--text-muted);">${dateStr}</td>
                <td class="text-right">
                    <button class="btn-action-icon" title="Reprocesar Flujo n8n" onclick="event.stopPropagation(); reprocessDoc('${doc.id}')">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"></polyline><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path></svg>
                    </button>
                </td>
            </tr>
        `;
    }).join('');
}

// Auto-Polling for Real-Time Updates
function startPolling() {
    if (state.pollingInterval) return;
    logTerminal('Iniciando sondeo de estado en tiempo real (polling 3s)...', 'info');
    state.pollingInterval = setInterval(async () => {
        try {
            const res = await fetch(`${API_BASE}/api/documents`);
            if (res.ok) {
                const updated = await res.json();
                
                // Check if any doc changed to LISTO
                updated.forEach(u => {
                    const old = state.documents.find(d => d.id === u.id);
                    if (old && old.status === 'PROCESANDO' && u.status === 'LISTO') {
                        logTerminal(`[Trigger 04] Callback recibido para '${u.fileName}' -> Estado: LISTO! Tags asignadas: ${u.tags.join(', ')}`, 'success');
                        showToast(`¡Documento procesado: ${u.fileName}!`, 'success');
                    }
                });

                state.documents = updated;
                updateMetrics(updated);
                renderTagChips(updated);
                renderDocumentsTable();

                const stillProcessing = updated.some(d => d.status === 'PROCESANDO');
                if (!stillProcessing) {
                    stopPolling();
                }
            }
        } catch (e) {
            console.error('Polling error', e);
        }
    }, 3000);
}

function stopPolling() {
    if (state.pollingInterval) {
        clearInterval(state.pollingInterval);
        state.pollingInterval = null;
        logTerminal('Todos los procesos activos han concluido. Sondeo pausado.', 'info');
    }
}

// Reprocess Trigger
window.reprocessDoc = async function(id) {
    try {
        logTerminal(`[Manual Trigger] Solicitando reprocesamiento de documento ${id}...`, 'info');
        const res = await fetch(`${API_BASE}/api/documents/${id}/reprocess`, { method: 'POST' });
        if (res.ok) {
            showToast('Reprocesamiento iniciado en n8n', 'info');
            await fetchDocuments();
            startPolling();
        } else {
            throw new Error(`HTTP ${res.status}`);
        }
    } catch (err) {
        logTerminal(`Error reprocesando: ${err.message}`, 'error');
        showToast('Error al reprocesar documento', 'error');
    }
};

/* ==========================================================================
   5. Semantic Search with Qdrant
   ========================================================================== */
function initSearch() {
    if (btnSearch && searchInput) {
        btnSearch.addEventListener('click', performSemanticSearch);
        searchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') performSemanticSearch();
        });
    }

    // Suggestions pills
    document.querySelectorAll('.suggestion-pills .pill').forEach(pill => {
        pill.addEventListener('click', () => {
            searchInput.value = pill.dataset.query;
            performSemanticSearch();
        });
    });
}

async function performSemanticSearch() {
    const query = searchInput.value.trim();
    if (!query) {
        showToast('Ingresa un texto para buscar en Qdrant', 'info');
        return;
    }

    searchResultsList.innerHTML = `
        <div class="search-empty-state">
            <svg class="spin-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10" stroke-dasharray="32" stroke-dashoffset="12"></circle></svg>
            <p>Calculando similitud coseno en Qdrant para: "<em>${escapeHtml(query)}</em>"...</p>
        </div>
    `;

    logTerminal(`[Qdrant Search] Ejecutando consulta semántica: "${query}"`, 'info');

    try {
        const res = await fetch(`${API_BASE}/api/search?q=${encodeURIComponent(query)}&limit=5`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const results = await res.json();
        renderSearchResults(results, query);

    } catch (err) {
        console.error('Error buscando:', err);
        searchResultsList.innerHTML = `
            <div class="search-empty-state">
                <p style="color: var(--rose);">Error al realizar búsqueda vectorial: ${err.message}</p>
            </div>
        `;
        logTerminal(`Error en búsqueda: ${err.message}`, 'error');
    }
}

function renderSearchResults(hits, query) {
    if (!hits || hits.length === 0) {
        searchResultsList.innerHTML = `
            <div class="search-empty-state">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
                <p>No se encontraron fragmentos semánticos relevantes para tu búsqueda.</p>
            </div>
        `;
        logTerminal(`[Qdrant Search] 0 resultados para "${query}"`, 'warn');
        return;
    }

    logTerminal(`[Qdrant Search] ${hits.length} fragmentos encontrados con alta similitud coseno.`, 'success');

    searchResultsList.innerHTML = hits.map((hit, index) => {
        const scorePercent = Math.round(Math.abs(hit.score || 0) * 100);
        const tags = Array.isArray(hit.tags) ? hit.tags.join(', ') : '';
        const snippetText = hit.matchingSnippet || hit.text || hit.snippet || hit.summary || 'Sin texto';

        return `
            <div class="search-hit-card">
                <div class="hit-header">
                    <div class="hit-doc-info">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path></svg>
                        <span>${escapeHtml(hit.fileName || 'Documento')}</span>
                    </div>
                    <div class="score-badge" title="Similitud Coseno de Embedding en Qdrant: ${hit.score}">
                        <div class="score-bar"><div class="score-fill" style="width: ${Math.max(scorePercent, 5)}%"></div></div>
                        <span>Coseno: ${(hit.score || 0).toFixed(4)} (${scorePercent}%)</span>
                    </div>
                </div>

                <div class="hit-snippet">
                    ${escapeHtml(snippetText)}
                </div>

                <div class="hit-footer">
                    <span>Etiquetas asignadas: <strong style="color: var(--primary);">${escapeHtml(tags)}</strong></span>
                    <span>Chunk #<strong>${hit.chunkIndex !== undefined ? hit.chunkIndex : 0}</strong> • Doc ID: <code>${hit.documentId ? hit.documentId.substring(0, 8) : '—'}...</code></span>
                </div>
            </div>
        `;
    }).join('');
}

/* ==========================================================================
   6. Diagnostics & Realtime Event Terminal
   ========================================================================== */
function initDiagnostics() {
    const btnRefreshHealth = document.getElementById('btn-refresh-health');
    if (btnRefreshHealth) {
        btnRefreshHealth.addEventListener('click', () => {
            checkHealth();
            showToast('Verificando estado de los servicios...', 'info');
        });
    }

    const btnSimulate = document.getElementById('btn-simulate-event');
    if (btnSimulate) {
        btnSimulate.addEventListener('click', () => {
            logTerminal('[Diagnóstico] Disparando ping de salud hacia PostgreSQL y Qdrant...', 'info');
            checkHealth();
        });
    }
}

async function checkHealth() {
    try {
        const res = await fetch(`${API_BASE}/api/system/diagnostics`);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        
        const data = await res.json();
        
        const isPostgresUp = (data.postgres?.status || data.postgres) === 'UP';
        const isQdrantUp = (data.qdrant?.status || data.qdrant) === 'UP';
        const isN8nUp = (data.n8n_trigger?.status || data.n8n_trigger) === 'UP';

        updateDot('dot-postgres', isPostgresUp);
        updateDot('dot-qdrant', isQdrantUp);
        updateDot('dot-n8n', isN8nUp);
        updateDot('dot-backend', true);

        logTerminal(`Diagnóstico: PostgreSQL=${isPostgresUp ? 'UP' : 'DOWN'}, Qdrant=${isQdrantUp ? 'UP' : 'DOWN'}, n8n=${isN8nUp ? 'UP' : 'DOWN'}`, 'info');

    } catch (err) {
        console.error('Error de diagnóstico:', err);
        updateDot('dot-postgres', false);
        updateDot('dot-qdrant', false);
        updateDot('dot-n8n', false);
        updateDot('dot-backend', false);
        logTerminal(`Fallo de conexión al backend Spring Boot: ${err.message}`, 'error');
    }
}

/* ==========================================================================
   Document Detail Modal Logic
   ========================================================================== */
window.showDocModal = function(id) {
    const doc = state.documents.find(d => d.id === id);
    if (!doc) return;

    document.getElementById('modal-doc-title').textContent = doc.fileName;
    document.getElementById('modal-doc-meta').textContent = `UUID: ${doc.id} • Tamaño: ${(doc.fileSize ? (doc.fileSize / 1024).toFixed(1) : 0)} KB • Tipo: ${doc.mimeType || 'application/pdf'}`;
    
    // Status
    const statusEl = document.getElementById('modal-doc-status');
    if (doc.status === 'LISTO') {
        statusEl.innerHTML = `<span class="badge-status status-ready"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg> LISTO (En Qdrant)</span>`;
    } else if (doc.status === 'PROCESANDO') {
        statusEl.innerHTML = `<span class="badge-status status-processing">PROCESANDO</span>`;
    } else {
        statusEl.innerHTML = `<span class="badge-status status-failed">FALLIDO</span>`;
    }

    document.getElementById('modal-doc-collection').textContent = doc.vectorCollection || 'autotag_documents';
    document.getElementById('modal-doc-chunks-count').textContent = doc.chunksCount || 1;

    // Tags
    const tagsContainer = document.getElementById('modal-doc-tags');
    if (Array.isArray(doc.tags) && doc.tags.length > 0) {
        tagsContainer.innerHTML = doc.tags.map(t => `<span class="tag-badge">${escapeHtml(t)}</span>`).join('');
    } else {
        tagsContainer.innerHTML = '<span class="text-muted" style="font-size:0.8rem">Sin etiquetas asignadas</span>';
    }

    // Summary
    const summaryEl = document.getElementById('modal-doc-summary');
    summaryEl.textContent = doc.summary || 'Extracción y resumen contextual en proceso...';

    // Chunks
    const chunksEl = document.getElementById('modal-doc-chunks');
    chunksEl.innerHTML = `
        <div style="margin-bottom: 6px; color: var(--primary); font-weight: 700;">Fragmento Chunk #0 (384 dimensiones - Cosine)</div>
        <div style="font-size: 0.8rem; color: var(--text-faint); margin-bottom: 6px;">Colección: <code>${escapeHtml(doc.vectorCollection || 'autotag_documents')}</code> | Doc ID: <code>${doc.id}</code></div>
        <div style="color: var(--text-main); font-size: 0.85rem; line-height: 1.6;">${escapeHtml(doc.summary || '')}</div>
    `;

    // Modal action buttons
    const btnSearch = document.getElementById('modal-btn-search');
    btnSearch.onclick = () => {
        closeDocModal();
        const searchBtn = document.querySelector('.nav-btn[data-tab="search"]');
        if (searchBtn) searchBtn.click();
        const searchInput = document.getElementById('input-semantic-search');
        if (searchInput) {
            searchInput.value = doc.tags && doc.tags.length > 0 ? doc.tags.join(' ') : doc.fileName;
            performSemanticSearch();
        }
    };

    const btnReprocess = document.getElementById('modal-btn-reprocess');
    btnReprocess.onclick = () => {
        closeDocModal();
        reprocessDoc(doc.id);
    };

    const modal = document.getElementById('doc-modal');
    if (modal) modal.style.display = 'flex';
};

window.closeDocModal = function() {
    const modal = document.getElementById('doc-modal');
    if (modal) modal.style.display = 'none';
};

function updateDot(id, isUp) {
    const el = document.getElementById(id);
    if (el) {
        el.className = 'dot ' + (isUp ? 'up' : 'down');
    }
}

function logTerminal(message, type = 'info') {
    if (!terminalScreen) return;

    const now = new Date();
    const timeStr = `[${now.toTimeString().split(' ')[0]}]`;
    
    let typeClass = 'log-info';
    if (type === 'success') typeClass = 'log-success';
    else if (type === 'warn') typeClass = 'log-warn';
    else if (type === 'error') typeClass = 'log-error';

    const line = document.createElement('div');
    line.className = 'log-line';
    line.innerHTML = `<span class="log-time">${timeStr}</span> <span class="${typeClass}">${escapeHtml(message)}</span>`;

    terminalScreen.appendChild(line);
    terminalScreen.scrollTop = terminalScreen.scrollHeight;
}

/* ==========================================================================
   7. Toast Notifications & Utility Helpers
   ========================================================================== */
function showToast(message, type = 'info') {
    const wrapper = document.getElementById('toast-wrapper');
    if (!wrapper) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = message;

    wrapper.appendChild(toast);

    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(60px)';
        setTimeout(() => toast.remove(), 300);
    }, 3500);
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}
