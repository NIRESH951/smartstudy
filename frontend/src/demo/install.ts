// Demo entry: installs the in-browser API before the app makes its first request,
// and shows a small notice explaining what the preview is.
import { installMockServer } from './mockServer';

installMockServer();

const bar = document.createElement('div');
bar.setAttribute('role', 'note');
bar.style.cssText =
  'position:fixed;left:50%;bottom:12px;transform:translateX(-50%);z-index:9999;max-width:calc(100% - 24px);' +
  'display:flex;align-items:center;gap:10px;padding:8px 12px;border-radius:999px;background:#0f172a;color:#e2e8f0;' +
  'font:12px/1.4 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;box-shadow:0 6px 20px rgba(15,23,42,.25)';
bar.innerHTML =
  '<span style="background:#10b981;color:#052e1b;font-weight:600;border-radius:999px;padding:2px 8px">DEMO</span>' +
  '<span>Runs in your browser with sample data &middot; changes reset on reload &middot; sign in with <b>demo@smartstudy.dev</b> / <b>Demo@12345</b></span>';
const close = document.createElement('button');
close.type = 'button';
close.setAttribute('aria-label', 'Hide demo notice');
close.textContent = '×';
close.style.cssText = 'background:none;border:0;color:#94a3b8;font-size:16px;cursor:pointer;padding:0 2px';
close.onclick = () => bar.remove();
bar.appendChild(close);
document.addEventListener('DOMContentLoaded', () => document.body.appendChild(bar));
if (document.readyState !== 'loading') document.body.appendChild(bar);
