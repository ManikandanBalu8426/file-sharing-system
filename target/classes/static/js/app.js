const API_URL = 'http://localhost:8080/api';

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

    try {
        const response = await fetch(`${API_URL}/auth/signin`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        const data = await response.json();

        if (response.ok) {
            localStorage.setItem('user', JSON.stringify(data));
            uiToast('ok', 'Signed in', 'Welcome back. Redirecting…');
            if (data.role === 'ROLE_ADMIN' || data.role === 'ROLE_AUDITOR') {
                // Option to go to admin dashboard
                window.location.href = 'admin.html';
            } else {
                window.location.href = 'dashboard.html';
            }
        } else {
            const msg = 'Login failed: ' + (data.message || 'Unknown error');
            document.getElementById('message').innerText = msg;
            uiToast('err', 'Login failed', data.message || 'Check your credentials');
        }
    } catch (error) {
        document.getElementById('message').innerText = 'Error: ' + error.message;
        uiToast('err', 'Network error', error.message);
    }
}

async function handleRegister(event) {
    event.preventDefault();
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    const role = document.getElementById('role').value;

    try {
        const response = await fetch(`${API_URL}/auth/signup`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password, role })
        });
        const data = await response.json();

        if (response.ok) {
            uiToast('ok', 'Account created', 'Please sign in to continue');
            window.location.href = 'login.html';
        } else {
            document.getElementById('message').innerText = 'Registration failed: ' + data.message;
            uiToast('err', 'Registration failed', data.message || 'Try a different username');
        }
    } catch (error) {
        document.getElementById('message').innerText = 'Error: ' + error.message;
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
    const response = await fetch(`${API_URL}/files/list`, {
        headers: authHeader()
    });
    if (response.status === 401) return logout();
    const files = await response.json();
    const list = document.getElementById('fileList');
    list.innerHTML = '';
    files.forEach(file => {
        const row = document.createElement('tr');
        // Dashboard uses Tailwind + ui.css. Keep classes compatible.
        row.className = 'border-b border-white/10 hover:bg-white/5 transition';
        row.innerHTML = `
            <td class="px-4 py-3 text-slate-200">${file.fileName}</td>
            <td class="px-4 py-3 text-slate-300">${file.owner.username}</td>
            <td class="px-4 py-3 text-slate-400 text-sm">${file.uploadTimestamp}</td>
            <td class="px-4 py-3">
                <button onclick="downloadFile(${file.id})" class="btn-ghost rounded-xl px-3 py-2 text-sm">Download</button>
            </td>
        `;
        list.appendChild(row);
    });
}

function setUploadProgress(percent) {
    const bar = document.getElementById('uploadBar');
    const label = document.getElementById('uploadPct');
    if (bar) bar.style.width = `${percent}%`;
    if (label) label.textContent = `${percent}%`;
}

function uploadFile() {
    const fileInput = document.getElementById('fileInput');
    const file = fileInput && fileInput.files ? fileInput.files[0] : null;
    if (!file) {
        uiToast('warn', 'No file selected', 'Choose a file to upload');
        return;
    }

    const formData = new FormData();
    formData.append('file', file);

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
            uiToast('ok', 'Upload complete', file.name);
            fileInput.value = '';
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

async function loadLogs() {
    const response = await fetch(`${API_URL}/admin/logs`, {
        headers: authHeader()
    });
    if (response.status === 401 || response.status === 403) {
        const container = document.getElementById('logsContainer');
        if (container) {
            container.innerHTML = '<div class="rounded-2xl border border-red-400/20 bg-red-400/10 p-4 text-red-200">Access Denied</div>';
        }
        uiToast('err', 'Access denied', 'Admin/Auditor role required');
        return;
    }
    const logs = await response.json();
    const list = document.getElementById('logList');
    list.innerHTML = '';
    logs.forEach(log => {
        const row = document.createElement('tr');
        row.className = 'border-b border-white/10 hover:bg-white/5 transition';
        row.innerHTML = `
            <td class="px-4 py-3 text-slate-200">${log.id}</td>
            <td class="px-4 py-3 text-slate-300">${log.user ? log.user.username : 'Unknown'}</td>
            <td class="px-4 py-3 text-slate-200">${log.action}</td>
            <td class="px-4 py-3 text-slate-300">${log.details || ''}</td>
            <td class="px-4 py-3 text-slate-400 text-sm">${log.timestamp}</td>
        `;
        list.appendChild(row);
    });
}

function checkAuth() {
    const user = JSON.parse(localStorage.getItem('user'));
    if (!user) {
        window.location.href = 'login.html';
    } else {
        document.getElementById('usernameDisplay').innerText = user.username;
        document.getElementById('roleDisplay').innerText = user.role;

        // Show Admin Link
        if (user.role === 'ROLE_ADMIN' || user.role === 'ROLE_AUDITOR') {
            document.getElementById('adminLink').classList.remove('hidden');
        }
    }
}
