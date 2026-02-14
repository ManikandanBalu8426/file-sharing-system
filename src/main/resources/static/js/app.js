const API_URL = 'http://localhost:8080/api';

let signupOtpRequestId = null;
let signinOtpRequestId = null;

function setAuthInputsDisabled(disabled) {
    const ids = ['username', 'email', 'password', 'role'];
    ids.forEach((id) => {
        const el = document.getElementById(id);
        if (el) el.disabled = !!disabled;
    });
}

function showOtpSection() {
    const section = document.getElementById('otpSection');
    if (section) section.classList.remove('hidden');
    const otp = document.getElementById('otp');
    if (otp) otp.focus();
}

function resetOtpState() {
    signupOtpRequestId = null;
    signinOtpRequestId = null;
    const otp = document.getElementById('otp');
    if (otp) otp.value = '';
    const section = document.getElementById('otpSection');
    if (section) section.classList.add('hidden');
    setAuthInputsDisabled(false);
    const registerBtn = document.getElementById('registerBtn');
    if (registerBtn) registerBtn.textContent = 'Create Account';
    const loginBtn = document.getElementById('loginBtn');
    if (loginBtn) loginBtn.textContent = 'Sign In';
}

async function resendOtp() {
    const messageEl = document.getElementById('message');
    if (messageEl) messageEl.innerText = '';

    const onRegisterPage = !!document.getElementById('registerBtn');
    const onLoginPage = !!document.getElementById('loginBtn');

    try {
        if (onRegisterPage) {
            const username = document.getElementById('username')?.value || '';
            const email = document.getElementById('email')?.value || '';
            const password = document.getElementById('password')?.value || '';
            const role = document.getElementById('role')?.value || 'user';

            const response = await fetch(`${API_URL}/auth/signup/request-otp`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, email, password, role })
            });
            const data = await response.json();
            if (response.ok) {
                signupOtpRequestId = data.otpRequestId;
                uiToast('ok', 'OTP resent', `Check ${email} for the code`);
                showOtpSection();
            } else {
                if (messageEl) messageEl.innerText = data.message || 'Could not resend OTP';
                uiToast('err', 'Resend failed', data.message || 'Try again');
            }
            return;
        }

        if (onLoginPage) {
            const username = document.getElementById('username')?.value || '';
            const password = document.getElementById('password')?.value || '';

            const response = await fetch(`${API_URL}/auth/signin/request-otp`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await response.json();
            if (response.ok) {
                signinOtpRequestId = data.otpRequestId;
                uiToast('ok', 'OTP resent', 'Check your email for the code');
                showOtpSection();
            } else {
                if (messageEl) messageEl.innerText = data.message || 'Could not resend OTP';
                uiToast('err', 'Resend failed', data.message || 'Try again');
            }
        }
    } catch (e) {
        if (messageEl) messageEl.innerText = 'Error: ' + e.message;
        uiToast('err', 'Network error', e.message);
    }
}

function wireOtpButtons() {
    const resendBtn = document.getElementById('resendOtpBtn');
    if (resendBtn) {
        resendBtn.addEventListener('click', async () => {
            const btn = resendBtn;
            try {
                window.UI?.setButtonLoading(btn, true, 'Sending…');
                await resendOtp();
            } finally {
                window.UI?.setButtonLoading(btn, false);
            }
        });
    }

    const changeBtn = document.getElementById('changeDetailsBtn');
    if (changeBtn) {
        changeBtn.addEventListener('click', () => {
            resetOtpState();
        });
    }
}

// Auto-wire for pages that include OTP controls.
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', wireOtpButtons);
} else {
    wireOtpButtons();
}

function uiToast(type, title, message) {
    if (window.UI && typeof window.UI.toast === 'function') {
        window.UI.toast({ type, title, message });
        return;
    }
    // Fallback when ui.js isn't loaded.
    if (type === 'err') {
        alert(`${title}${message ? ': ' + message : ''}`);
    }
}

async function handleLogin(event) {
    event.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    const otpEl = document.getElementById('otp');
    const messageEl = document.getElementById('message');
    if (messageEl) messageEl.innerText = '';

    try {
        if (!signinOtpRequestId) {
            const response = await fetch(`${API_URL}/auth/signin/request-otp`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await response.json();

            if (response.ok) {
                signinOtpRequestId = data.otpRequestId;
                uiToast('ok', 'OTP sent', 'Check your email for the code');
                showOtpSection();
                setAuthInputsDisabled(true);
                const btn = document.getElementById('loginBtn');
                if (btn) btn.textContent = 'Verify OTP';
            } else {
                const msg = 'Login failed: ' + (data.message || 'Unknown error');
                if (messageEl) messageEl.innerText = msg;
                uiToast('err', 'Login failed', data.message || 'Check your credentials');
            }
            return;
        }

        const otp = otpEl ? otpEl.value : '';
        const response = await fetch(`${API_URL}/auth/signin/verify`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ otpRequestId: signinOtpRequestId, otp })
        });
        const data = await response.json();

        if (response.ok) {
            localStorage.setItem('user', JSON.stringify(data));
            uiToast('ok', 'Signed in', 'Welcome back. Redirecting…');
            if (data.role === 'ROLE_ADMIN' || data.role === 'ROLE_AUDITOR') {
                window.location.href = 'admin.html';
            } else {
                window.location.href = 'dashboard.html';
            }
        } else {
            const msg = 'OTP verification failed: ' + (data.message || 'Unknown error');
            if (messageEl) messageEl.innerText = msg;
            uiToast('err', 'OTP failed', data.message || 'Try again');
            if ((data.message || '').toLowerCase().includes('expired') || (data.message || '').toLowerCase().includes('not found')) {
                resetOtpState();
            }
        }
    } catch (error) {
        if (messageEl) messageEl.innerText = 'Error: ' + error.message;
        uiToast('err', 'Network error', error.message);
    }
}

async function handleRegister(event) {
    event.preventDefault();
    const username = document.getElementById('username').value;
    const emailEl = document.getElementById('email');
    const email = emailEl ? emailEl.value : '';
    const password = document.getElementById('password').value;
    const role = document.getElementById('role').value;
    const otpEl = document.getElementById('otp');
    const messageEl = document.getElementById('message');
    if (messageEl) messageEl.innerText = '';

    try {
        if (!signupOtpRequestId) {
            const response = await fetch(`${API_URL}/auth/signup/request-otp`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, email, password, role })
            });
            const data = await response.json();

            if (response.ok) {
                signupOtpRequestId = data.otpRequestId;
                uiToast('ok', 'OTP sent', `Check ${email} for the code`);
                showOtpSection();
                setAuthInputsDisabled(true);
                const btn = document.getElementById('registerBtn');
                if (btn) btn.textContent = 'Verify OTP';
            } else {
                if (messageEl) messageEl.innerText = 'Registration failed: ' + (data.message || 'Unknown error');
                uiToast('err', 'Registration failed', data.message || 'Try again');
            }
            return;
        }

        const otp = otpEl ? otpEl.value : '';
        const response = await fetch(`${API_URL}/auth/signup/verify`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ otpRequestId: signupOtpRequestId, otp })
        });
        const data = await response.json();

        if (response.ok) {
            uiToast('ok', 'Account verified', 'Please sign in to continue');
            window.location.href = 'login.html';
        } else {
            if (messageEl) messageEl.innerText = 'OTP verification failed: ' + (data.message || 'Unknown error');
            uiToast('err', 'OTP failed', data.message || 'Try again');
            if ((data.message || '').toLowerCase().includes('expired') || (data.message || '').toLowerCase().includes('not found')) {
                resetOtpState();
            }
        }
    } catch (error) {
        if (messageEl) messageEl.innerText = 'Error: ' + error.message;
        uiToast('err', 'Network error', error.message);
    }
}

function logout() {
    localStorage.removeItem('user');
    window.location.href = 'login.html';
}

function authHeader() {
    const user = JSON.parse(localStorage.getItem('user'));
    if (user && user.token) {
        return { 'Authorization': 'Bearer ' + user.token };
    } else {
        return {};
    }
}

async function loadFiles() {
    const response = await fetch(`${API_URL}/files/list`, { headers: authHeader() });
    if (response.status === 401) return logout();

    const files = await response.json();
    window.__vaultFiles = Array.isArray(files) ? files : [];

    // Wire dashboard controls (safe no-op on other pages)
    wireDashboardControlsOnce();
    updateCategoryCounts(window.__vaultFiles);
    renderFileTable();
}

function initFilesPage() {
    // Used by files.html (safe no-op on other pages)
    try {
        const url = new URL(window.location.href);
        const type = (url.searchParams.get('type') || '').toLowerCase();
        const allowed = new Set(['images', 'documents', 'videos', 'audio', 'archives']);

        if (allowed.has(type)) {
            window.__vaultTypeFilter = type;
        } else {
            window.__vaultTypeFilter = null;
        }

        const titleEl = document.getElementById('filesTitle');
        const subtitleEl = document.getElementById('filesSubtitle');
        const crumbEl = document.getElementById('filesBreadcrumb');
        const clearLink = document.getElementById('clearTypeLink');

        if (window.__vaultTypeFilter) {
            const pretty = window.__vaultTypeFilter.charAt(0).toUpperCase() + window.__vaultTypeFilter.slice(1);
            if (titleEl) titleEl.textContent = `${pretty} — Accessible Files`;
            if (subtitleEl) subtitleEl.textContent = `Showing only ${pretty.toLowerCase()} you’re allowed to access.`;
            if (crumbEl) crumbEl.textContent = `Dashboard / ${pretty}`;
            if (clearLink) clearLink.classList.remove('hidden');
        } else {
            if (titleEl) titleEl.textContent = 'Accessible Files';
            if (subtitleEl) subtitleEl.textContent = 'Files are listed strictly by role and permissions (not folder paths).';
            if (crumbEl) crumbEl.textContent = 'Dashboard / Files';
        }
    } catch (e) {
        // ignore
    }
}

function wireDashboardControlsOnce() {
    if (window.__dashboardWired) return;
    window.__dashboardWired = true;

    // Type filter cards
    // Only bind buttons (dashboard now uses links to files.html)
    document.querySelectorAll('button[data-type]').forEach(btn => {
        btn.addEventListener('click', () => {
            window.__vaultTypeFilter = btn.getAttribute('data-type') || null;
            setActiveTypeCard();
            renderFileTable();
        });
    });

    const clearType = document.getElementById('clearTypeFilter');
    if (clearType) {
        // Only attach if it's a button; links should navigate normally
        if ((clearType.tagName || '').toLowerCase() === 'button') {
            clearType.addEventListener('click', () => {
                window.__vaultTypeFilter = null;
                setActiveTypeCard();
                renderFileTable();
            });
        }
    }

    // Ownership segmented control
    document.querySelectorAll('.segmented [data-owner]').forEach(btn => {
        btn.addEventListener('click', () => {
            window.__vaultOwnerFilter = btn.getAttribute('data-owner') || 'all';
            setActiveOwnerPill();
            renderFileTable();
        });
    });

    const search = document.getElementById('searchInput');
    if (search) {
        search.addEventListener('input', () => {
            window.__vaultSearch = search.value || '';
            renderFileTable();
        });
    }

    // Selected files label
    const fileInput = document.getElementById('fileInput');
    if (fileInput) {
        fileInput.addEventListener('change', () => updateSelectedFilesLabel());
    }

    // Defaults
    if (!window.__vaultOwnerFilter) window.__vaultOwnerFilter = 'all';
    if (!window.__vaultTypeFilter) window.__vaultTypeFilter = null;
    if (!window.__vaultSearch) window.__vaultSearch = '';
    setActiveOwnerPill();
    setActiveTypeCard();
}

function updateSelectedFilesLabel() {
    const out = document.getElementById('selectedFiles');
    const input = document.getElementById('fileInput');
    if (!out || !input || !input.files) return;

    const files = Array.from(input.files);
    if (!files.length) {
        out.textContent = '';
        return;
    }
    if (files.length === 1) {
        out.textContent = `Selected: ${files[0].name}`;
        return;
    }
    out.textContent = `Selected: ${files.length} files`;
}

function setActiveOwnerPill() {
    const current = window.__vaultOwnerFilter || 'all';
    document.querySelectorAll('.segmented [data-owner]').forEach(btn => {
        const isActive = (btn.getAttribute('data-owner') === current);
        btn.classList.toggle('is-active', isActive);
        btn.setAttribute('aria-selected', String(isActive));
    });
}

function setActiveTypeCard() {
    const current = window.__vaultTypeFilter;
    document.querySelectorAll('[data-type]').forEach(btn => {
        const isActive = current && btn.getAttribute('data-type') === current;
        btn.classList.toggle('is-active', !!isActive);
        btn.setAttribute('aria-pressed', String(!!isActive));
    });
}

function updateCategoryCounts(files) {
    const counts = { images: 0, documents: 0, videos: 0, audio: 0, archives: 0 };
    (files || []).forEach(f => {
        const t = classifyFileType(f?.fileName || '');
        if (counts[t] !== undefined) counts[t] += 1;
    });

    const set = (id, n) => { const el = document.getElementById(id); if (el) el.textContent = String(n); };
    set('countImages', counts.images);
    set('countDocuments', counts.documents);
    set('countVideos', counts.videos);
    set('countAudio', counts.audio);
    set('countArchives', counts.archives);
}

function classifyFileType(fileName) {
    const name = (fileName || '').toLowerCase();
    const ext = (name.includes('.') ? name.split('.').pop() : '') || '';

    const isImg = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'avif', 'svg'].includes(ext);
    const isVideo = ['mp4', 'mov', 'avi', 'mkv', 'webm', 'm4v'].includes(ext);
    const isAudio = ['mp3', 'wav', 'flac', 'aac', 'ogg', 'm4a'].includes(ext);
    const isArchive = ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz'].includes(ext);

    if (isImg) return 'images';
    if (isVideo) return 'videos';
    if (isAudio) return 'audio';
    if (isArchive) return 'archives';
    return 'documents';
}

function fileIconEmoji(fileName) {
    const type = classifyFileType(fileName);
    if (type === 'images') return '🖼️';
    if (type === 'videos') return '🎬';
    if (type === 'audio') return '🎧';
    if (type === 'archives') return '🗜️';
    return '📄';
}

function formatTimestamp(ts) {
    if (!ts) return '';
    // Server returns LocalDateTime (often like 2026-02-02T12:30:15.123)
    const parsed = new Date(ts);
    if (!isNaN(parsed.getTime())) {
        return parsed.toLocaleString();
    }
    // Fallback for non-ISO strings
    return String(ts).replace('T', ' ');
}

function normalizeVisibility(v) {
    const s = (v || '').toString().toUpperCase();
    if (s === 'PUBLIC') return 'PUBLIC';
    if (s === 'PROTECTED') return 'PROTECTED';
    return 'PRIVATE';
}

function renderFileTable() {
    const list = document.getElementById('fileList');
    if (!list) return;

    const user = JSON.parse(localStorage.getItem('user') || 'null');
    const myUsername = user?.username || '';

    const typeFilter = window.__vaultTypeFilter;
    const ownerFilter = window.__vaultOwnerFilter || 'all';
    const search = (window.__vaultSearch || '').trim().toLowerCase();

    const allFiles = Array.isArray(window.__vaultFiles) ? window.__vaultFiles : [];
    const filtered = allFiles.filter(f => {
        if (!f) return false;
        const fileType = classifyFileType(f.fileName);
        if (typeFilter && fileType !== typeFilter) return false;

        const isMine = (f.ownerUsername || '') === myUsername;
        if (ownerFilter === 'mine' && !isMine) return false;
        if (ownerFilter === 'shared' && isMine) return false;

        if (search) {
            const hay = `${f.fileName || ''} ${f.ownerUsername || ''} ${f.category || ''} ${f.purpose || ''}`.toLowerCase();
            if (!hay.includes(search)) return false;
        }
        return true;
    });

    // Hint text
    const hint = document.getElementById('activeFilterHint');
    if (hint) {
        const parts = [];
        if (typeFilter) parts.push(`Type: ${typeFilter}`);
        if (ownerFilter !== 'all') parts.push(ownerFilter === 'mine' ? 'My files' : 'Shared');
        if (search) parts.push('Search active');
        hint.textContent = parts.length ? parts.join(' • ') : '';
    }

    list.innerHTML = '';

    const empty = document.getElementById('emptyState');
    if (empty) empty.classList.toggle('hidden', filtered.length !== 0);

    // Group separation: show headings in "All" mode
    const shouldGroup = ownerFilter === 'all';
    const mine = filtered.filter(f => (f.ownerUsername || '') === myUsername);
    const shared = filtered.filter(f => (f.ownerUsername || '') !== myUsername);

    const appendGroup = (label, items) => {
        if (!items.length) return;

        if (shouldGroup) {
            const groupRow = document.createElement('tr');
            groupRow.className = 'group-row border-b border-white/10';
            groupRow.innerHTML = `<td class="px-4 py-2" colspan="6">${label}</td>`;
            list.appendChild(groupRow);
        }

        items.forEach(file => {
            const canDownload = (typeof file.canDownload === 'boolean') ? file.canDownload : true;
            const isMineRow = (file.ownerUsername || '') === myUsername;
            const visibility = normalizeVisibility(file.visibilityType);

            const accessLabel = canDownload ? 'Download' : 'View only';
            const accessIcon = canDownload ? '⬇️' : '👁️';

            const visClass = visibility.toLowerCase();
            const visDot = `<span class="dot" aria-hidden="true"></span>`;

            const filenameSafe = (file.fileName || '').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
            const ownerSafe = (file.ownerUsername || '').replaceAll('<', '&lt;').replaceAll('>', '&gt;');

            const row = document.createElement('tr');
            row.className = 'border-b border-white/10 hover:bg-white/5 transition';
            row.innerHTML = `
                <td class="px-4 py-3 text-slate-200">
                    <div class="file-name">
                        <div class="file-type" aria-hidden="true">${fileIconEmoji(file.fileName)}</div>
                        <div>
                            <div style="font-weight:650">${filenameSafe}</div>
                            <div class="text-xs text-slate-400">${isMineRow ? 'Owned by you' : 'Shared / department'}</div>
                        </div>
                    </div>
                </td>
                <td class="px-4 py-3 text-slate-300">${ownerSafe}</td>
                <td class="px-4 py-3 text-slate-400 text-sm">${formatTimestamp(file.uploadTimestamp)}</td>
                <td class="px-4 py-3">
                    <span class="badge">
                        <span aria-hidden="true">${accessIcon}</span>
                        <span>${accessLabel}</span>
                    </span>
                </td>
                <td class="px-4 py-3">
                    <span class="badge ${visClass}">${visDot}<span>${visibility}</span></span>
                </td>
                <td class="px-4 py-3">
                    ${canDownload
                        ? `<button onclick="downloadFile(${file.id})" class="btn-primary rounded-xl px-3 py-2 text-sm font-semibold">Download</button>`
                        : `<button class="btn-primary btn-disabled rounded-xl px-3 py-2 text-sm font-semibold" disabled title="You have view access but not download permission">Download</button>`
                    }
                </td>
            `;
            list.appendChild(row);
        });
    };

    appendGroup('Your files', mine);
    appendGroup('Shared with you / departments', shared);

}

function setUploadProgress(percent) {
    const bar = document.getElementById('uploadBar');
    const label = document.getElementById('uploadPct');
    if (bar) bar.style.width = `${percent}%`;
    if (label) label.textContent = `${percent}%`;
}

function uploadFile() {
    const fileInput = document.getElementById('fileInput');
    const files = fileInput && fileInput.files ? Array.from(fileInput.files) : [];
    if (!files.length) {
        uiToast('warn', 'No file selected', 'Choose one or more files to upload');
        return;
    }

    const formData = new FormData();
    // Backend accepts MultipartFile[] with parameter name "file".
    files.forEach(f => formData.append('file', f));

    // Optional metadata (only sent if the inputs exist on the page)
    const visibility = document.getElementById('visibilitySelect')?.value;
    const purpose = document.getElementById('purposeInput')?.value;
    const category = document.getElementById('categoryInput')?.value;

    if (visibility) formData.append('visibility', visibility);
    if (purpose) formData.append('purpose', purpose);
    if (category) formData.append('category', category);

    const btn = document.getElementById('uploadBtn');
    window.UI?.setButtonLoading(btn, true, 'Uploading…');
    setUploadProgress(0);

    // Use XHR so we can display upload progress.
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_URL}/files/upload`, true);

    const auth = authHeader();
    if (auth && auth.Authorization) xhr.setRequestHeader('Authorization', auth.Authorization);

    xhr.upload.onprogress = (e) => {
        if (!e.lengthComputable) return;
        const pct = Math.max(1, Math.min(99, Math.round((e.loaded / e.total) * 100)));
        setUploadProgress(pct);
    };

    xhr.onload = async () => {
        window.UI?.setButtonLoading(btn, false);
        if (xhr.status === 401) return logout();
        if (xhr.status >= 200 && xhr.status < 300) {
            setUploadProgress(100);
            const msg = files.length === 1 ? files[0].name : `${files.length} files`;
            uiToast('ok', 'Upload complete', msg);
            fileInput.value = '';
            updateSelectedFilesLabel();
            await loadFiles();
            window.setTimeout(() => setUploadProgress(0), 600);
        } else {
            uiToast('err', 'Upload failed', xhr.responseText || 'Server rejected the upload');
            setUploadProgress(0);
        }
    };

    xhr.onerror = () => {
        window.UI?.setButtonLoading(btn, false);
        uiToast('err', 'Network error', 'Could not upload the file');
        setUploadProgress(0);
    };

    xhr.send(formData);
}

async function downloadFile(id) {
    const response = await fetch(`${API_URL}/files/download/${id}`, {
        headers: authHeader()
    });

    if (response.ok) {
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        // Try to get filename from header
        const contentDisposition = response.headers.get('Content-Disposition');
        let filename = 'downloaded_file';
        if (contentDisposition) {
            const items = contentDisposition.split(';');
            for (let item of items) {
                if (item.trim().startsWith('filename=')) {
                    filename = item.split('=')[1].replace(/"/g, '');
                }
            }
        }
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
    } else {
        uiToast('err', 'Download failed', 'Unauthorized or file not available');
    }
}

// ==================== AUDIT LOG FUNCTIONS ====================

let auditState = {
    currentPage: 0,
    pageSize: 20,
    totalPages: 0,
    totalItems: 0,
    filters: {
        username: '',
        action: '',
        status: '',
        fileName: '',
        startDate: '',
        endDate: ''
    }
};

/**
 * Initialize the audit page - load filters, stats, and logs
 */
async function initAuditPage() {
    await loadAuditFilters();
    await loadAuditStats();
    await loadAuditLogs();
}

/**
 * Load filter dropdown options from the API
 */
async function loadAuditFilters() {
    try {
        const response = await fetch(`${API_URL}/audit/filters`, {
            headers: authHeader()
        });
        if (response.ok) {
            const data = await response.json();
            const actionSelect = document.getElementById('filterAction');
            if (actionSelect && data.actions) {
                actionSelect.innerHTML = '<option value="">All Actions</option>';
                data.actions.forEach(action => {
                    const opt = document.createElement('option');
                    opt.value = action;
                    opt.textContent = formatActionLabel(action);
                    actionSelect.appendChild(opt);
                });
            }
        }
    } catch (e) {
        console.error('Failed to load filters:', e);
    }
}

/**
 * Load audit statistics
 */
async function loadAuditStats() {
    try {
        const response = await fetch(`${API_URL}/audit/stats`, {
            headers: authHeader()
        });
        if (response.ok) {
            const stats = await response.json();
            const totalEl = document.getElementById('totalLogs');
            const successEl = document.getElementById('successCount');
            const failureEl = document.getElementById('failureCount');
            if (totalEl) totalEl.textContent = stats.totalLogs || 0;
            if (successEl) successEl.textContent = stats.successCount || 0;
            if (failureEl) failureEl.textContent = stats.failureCount || 0;
        }
    } catch (e) {
        console.error('Failed to load stats:', e);
    }
}

/**
 * Load audit logs with current filters and pagination
 */
async function loadAuditLogs() {
    const params = new URLSearchParams();
    params.append('page', auditState.currentPage);
    params.append('size', auditState.pageSize);

    if (auditState.filters.username) params.append('username', auditState.filters.username);
    if (auditState.filters.action) params.append('action', auditState.filters.action);
    if (auditState.filters.status) params.append('status', auditState.filters.status);
    if (auditState.filters.fileName) params.append('fileName', auditState.filters.fileName);
    if (auditState.filters.startDate) params.append('startDate', new Date(auditState.filters.startDate).toISOString());
    if (auditState.filters.endDate) params.append('endDate', new Date(auditState.filters.endDate).toISOString());

    try {
        const response = await fetch(`${API_URL}/audit?${params.toString()}`, {
            headers: authHeader()
        });

        if (response.status === 401 || response.status === 403) {
            uiToast('err', 'Access denied', 'Admin/Auditor role required');
            return;
        }

        const data = await response.json();
        auditState.totalPages = data.totalPages || 1;
        auditState.totalItems = data.totalItems || 0;

        renderAuditTable(data.logs || []);
        updatePagination();
    } catch (e) {
        console.error('Failed to load audit logs:', e);
        uiToast('err', 'Load failed', 'Could not fetch audit logs');
    }
}

/**
 * Render the audit logs table
 */
function renderAuditTable(logs) {
    const list = document.getElementById('logList');
    if (!list) return;
    list.innerHTML = '';

    if (logs.length === 0) {
        const row = document.createElement('tr');
        row.className = 'border-b border-white/10';
        row.innerHTML = `<td class="px-4 py-6 text-slate-300 text-center" colspan="7">No audit logs found.</td>`;
        list.appendChild(row);
        return;
    }

    logs.forEach(log => {
        const row = document.createElement('tr');
        row.className = 'border-b border-white/10 hover:bg-white/5 transition';
        row.innerHTML = `
            <td class="px-4 py-3 text-slate-400 font-mono text-xs">${log.id}</td>
            <td class="px-4 py-3 text-slate-200 font-medium">${escapeHtml(log.username || 'SYSTEM')}</td>
            <td class="px-4 py-3">${renderActionBadge(log.action)}</td>
            <td class="px-4 py-3">
                <div class="file-name text-slate-300" title="${escapeHtml(log.fileName || '')}">${escapeHtml(log.fileName || '-')}</div>
            </td>
            <td class="px-4 py-3 text-slate-400 font-mono text-xs">${log.resourceId || '-'}</td>
            <td class="px-4 py-3">${renderStatusBadge(log.status)}</td>
            <td class="px-4 py-3 text-slate-400 text-sm">${formatTimestamp(log.timestamp)}</td>
        `;
        list.appendChild(row);
    });
}

/**
 * Render action type as a colored badge
 */
function renderActionBadge(action) {
    if (!action) return '<span class="badge badge-default">UNKNOWN</span>';

    const badgeClass = getActionBadgeClass(action);
    const label = formatActionLabel(action);
    return `<span class="badge ${badgeClass}">${label}</span>`;
}

/**
 * Get CSS class for action badge
 */
function getActionBadgeClass(action) {
    const map = {
        'LOGIN_SUCCESS': 'badge-login-success',
        'LOGIN_FAILED': 'badge-login-failed',
        'UPLOAD': 'badge-upload',
        'DOWNLOAD': 'badge-download',
        'DELETE': 'badge-delete',
        'SHARE': 'badge-share',
        'ROLE_UPDATE': 'badge-role-update',
        'PERMISSION_UPDATE': 'badge-permission-update',
        'VISIBILITY_UPDATE': 'badge-visibility-update',
        'ACCESS_REQUEST': 'badge-access-request',
        'ACCESS_GRANT': 'badge-access-grant',
        'ACCESS_DENY': 'badge-access-deny'
    };
    return map[action] || 'badge-default';
}

/**
 * Format action type for display
 */
function formatActionLabel(action) {
    if (!action) return 'Unknown';
    return action.replace(/_/g, ' ');
}

/**
 * Render status as a colored badge
 */
function renderStatusBadge(status) {
    if (!status) return '<span class="badge badge-default">-</span>';
    const badgeClass = status === 'SUCCESS' ? 'status-success' : 'status-failure';
    return `<span class="badge ${badgeClass}">${status}</span>`;
}

/**
 * Format timestamp to human-readable format
 * Converts 2026-02-13T12:38:49.494338 to "13 Feb 2026, 12:38 PM"
 */
function formatTimestamp(timestamp) {
    if (!timestamp) return '-';
    try {
        const date = new Date(timestamp);
        const day = date.getDate();
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const month = months[date.getMonth()];
        const year = date.getFullYear();

        let hours = date.getHours();
        const minutes = date.getMinutes().toString().padStart(2, '0');
        const ampm = hours >= 12 ? 'PM' : 'AM';
        hours = hours % 12;
        hours = hours ? hours : 12;

        return `${day} ${month} ${year}, ${hours}:${minutes} ${ampm}`;
    } catch (e) {
        return timestamp;
    }
}

/**
 * Update pagination controls
 */
function updatePagination() {
    const currentPageEl = document.getElementById('currentPage');
    const totalPagesEl = document.getElementById('totalPages');
    const totalRecordsEl = document.getElementById('totalRecords');
    const showingFromEl = document.getElementById('showingFrom');
    const showingToEl = document.getElementById('showingTo');
    const prevBtn = document.getElementById('prevPage');
    const nextBtn = document.getElementById('nextPage');

    if (currentPageEl) currentPageEl.textContent = auditState.currentPage + 1;
    if (totalPagesEl) totalPagesEl.textContent = auditState.totalPages || 1;
    if (totalRecordsEl) totalRecordsEl.textContent = auditState.totalItems;

    const from = auditState.totalItems === 0 ? 0 : (auditState.currentPage * auditState.pageSize) + 1;
    const to = Math.min((auditState.currentPage + 1) * auditState.pageSize, auditState.totalItems);
    if (showingFromEl) showingFromEl.textContent = from;
    if (showingToEl) showingToEl.textContent = to;

    if (prevBtn) prevBtn.disabled = auditState.currentPage === 0;
    if (nextBtn) nextBtn.disabled = auditState.currentPage >= auditState.totalPages - 1;
}

/**
 * Go to previous page
 */
function prevPage() {
    if (auditState.currentPage > 0) {
        auditState.currentPage--;
        loadAuditLogs();
    }
}

/**
 * Go to next page
 */
function nextPage() {
    if (auditState.currentPage < auditState.totalPages - 1) {
        auditState.currentPage++;
        loadAuditLogs();
    }
}

/**
 * Apply filters from the filter inputs
 */
function applyFilters() {
    auditState.filters.username = document.getElementById('filterUsername')?.value || '';
    auditState.filters.action = document.getElementById('filterAction')?.value || '';
    auditState.filters.status = document.getElementById('filterStatus')?.value || '';
    auditState.filters.fileName = document.getElementById('filterFileName')?.value || '';
    auditState.filters.startDate = document.getElementById('filterStartDate')?.value || '';
    auditState.filters.endDate = document.getElementById('filterEndDate')?.value || '';
    auditState.currentPage = 0; // Reset to first page
    loadAuditLogs();
}

/**
 * Clear all filters
 */
function clearFilters() {
    document.getElementById('filterUsername').value = '';
    document.getElementById('filterAction').value = '';
    document.getElementById('filterStatus').value = '';
    document.getElementById('filterFileName').value = '';
    document.getElementById('filterStartDate').value = '';
    document.getElementById('filterEndDate').value = '';
    auditState.filters = {
        username: '',
        action: '',
        status: '',
        fileName: '',
        startDate: '',
        endDate: ''
    };
    auditState.currentPage = 0;
    loadAuditLogs();
}

/**
 * Export audit logs as CSV
 */
async function exportAuditLogs() {
    const params = new URLSearchParams();
    if (auditState.filters.username) params.append('username', auditState.filters.username);
    if (auditState.filters.action) params.append('action', auditState.filters.action);
    if (auditState.filters.status) params.append('status', auditState.filters.status);
    if (auditState.filters.fileName) params.append('fileName', auditState.filters.fileName);
    if (auditState.filters.startDate) params.append('startDate', new Date(auditState.filters.startDate).toISOString());
    if (auditState.filters.endDate) params.append('endDate', new Date(auditState.filters.endDate).toISOString());

    try {
        const response = await fetch(`${API_URL}/audit/export?${params.toString()}`, {
            headers: authHeader()
        });

        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `audit_logs_${new Date().toISOString().split('T')[0]}.csv`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            window.URL.revokeObjectURL(url);
            uiToast('ok', 'Export complete', 'CSV file downloaded');
        } else {
            uiToast('err', 'Export failed', 'Could not download CSV');
        }
    } catch (e) {
        console.error('Export failed:', e);
        uiToast('err', 'Export failed', e.message);
    }
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Legacy function for backward compatibility
async function loadLogs() {
    await loadAuditLogs();
}

function checkAuth() {
    const raw = localStorage.getItem('user');
    if (!raw) {
        window.location.href = 'login.html';
        return;
    }

    let user;
    try {
        user = JSON.parse(raw);
    } catch (e) {
        localStorage.removeItem('user');
        localStorage.removeItem('token');
        window.location.href = 'login.html';
        return;
    }

    if (!user) {
        window.location.href = 'login.html';
        return;
    }

    const usernameDisplay = document.getElementById('usernameDisplay');
    if (usernameDisplay && user.username) {
        usernameDisplay.innerText = user.username;
    }
    const roleDisplay = document.getElementById('roleDisplay');
    if (roleDisplay && user.role) {
        roleDisplay.innerText = user.role;
    }
    const adminLink = document.getElementById('adminLink');
    if (adminLink && (user.role === 'ROLE_ADMIN' || user.role === 'ROLE_AUDITOR')) {
        adminLink.classList.remove('hidden');
    }
}
