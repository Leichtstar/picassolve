const rawOrigin = import.meta.env.VITE_BACKEND_ORIGIN;
const backendOrigin = rawOrigin ? rawOrigin.replace(/\/+$/, '') : '';

export const backendUrl = (path = '') => {
  if (!backendOrigin) return path;
  if (!path) return backendOrigin;
  if (path.startsWith('http://') || path.startsWith('https://')) return path;
  if (path.startsWith('/')) return `${backendOrigin}${path}`;
  return `${backendOrigin}/${path}`;
};

export const backendFetch = (path, init) => fetch(backendUrl(path), init);
