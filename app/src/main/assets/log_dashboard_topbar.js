(function () {
  const style = `
    .dashboard-topbar {
      position: sticky;
      top: calc(8px + env(safe-area-inset-top));
      z-index: 10;
      display: grid;
      grid-template-columns: 40px minmax(112px, 1fr) 40px;
      align-items: center;
      gap: 8px;
      width: min(980px, calc(100% - 32px));
      min-height: 56px;
      margin: calc(8px + env(safe-area-inset-top)) auto 14px;
      padding: 7px 8px;
      border: 1px solid var(--outline);
      border-radius: 8px;
      background: var(--surface);
    }
    .dashboard-nav-button {
      width: 40px;
      height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      border: 0;
      border-radius: 6px;
      color: var(--text);
      text-decoration: none;
      background: transparent;
      -webkit-tap-highlight-color: transparent;
    }
    .dashboard-nav-button:active { background: var(--bg); }
    .dashboard-nav-button.back { font-size: 0; }
    .dashboard-nav-button.back::before {
      content: "";
      width: 10px;
      height: 10px;
      border-left: 2px solid currentColor;
      border-bottom: 2px solid currentColor;
      transform: rotate(45deg);
      margin-left: 3px;
    }
    .dashboard-refresh-icon {
      width: 20px;
      height: 20px;
      fill: none;
      stroke: currentColor;
      stroke-linecap: round;
      stroke-linejoin: round;
      stroke-width: 2;
    }
    .dashboard-topbar-title { min-width: 0; text-align: center; }
    .dashboard-topbar-title h1 { margin: 0; font-size: 19px; line-height: 1.2; }
    .dashboard-topbar-title p {
      margin: 3px 0 0;
      color: var(--muted);
      font-size: 12px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `;

  window.mountLogDashboardTopbar = function (subtitle) {
    const styleElement = document.createElement("style");
    styleElement.textContent = style;
    document.head.appendChild(styleElement);

    const topbar = document.createElement("header");
    topbar.className = "dashboard-topbar";
    topbar.innerHTML = `
      <a class="dashboard-nav-button back" href="dnssr://back" aria-label="返回"></a>
      <div class="dashboard-topbar-title">
        <h1>日志仪表盘</h1>
        <p id="updated">${subtitle}</p>
      </div>
      <a class="dashboard-nav-button" href="dnssr://refresh" aria-label="刷新">
        <svg class="dashboard-refresh-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M21 12a9 9 0 0 1-15.5 6.2L3 16" />
          <path d="M3 21v-5h5" />
          <path d="M3 12a9 9 0 0 1 15.5-6.2L21 8" />
          <path d="M21 3v5h-5" />
        </svg>
      </a>`;
    document.body.prepend(topbar);
  };
}());
