document.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('plugins');
  if (root && typeof loadPluginsView === 'function') loadPluginsView(root);
});
