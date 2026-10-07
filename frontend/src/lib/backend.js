const rawOrigin = import.meta.env.VITE_BACKEND_ORIGIN;
const backendOrigin = rawOrigin ? rawOrigin.replace(/\/+$/, '') : '';

export const backendUrl = (path = '') => {
  if (!backendOrigin) return path;
  if (!path) return backendOrigin;
  if (path.startsWith('http://') || path.startsWith('https://')) return path;
  if (path.startsWith('/')) return `${backendOrigin}${path}`;
  return `${backendOrigin}/${path}`;
};

let onUnauthorized = null;

/** Register a handler invoked when an API call returns 401 (session expired). */
export const setUnauthorizedHandler = (handler) => {
  onUnauthorized = handler;
};

export const backendFetch = async (path, init) => {
  const res = await fetch(backendUrl(path), init);
  if (res.status === 401 && typeof onUnauthorized === 'function') {
    onUnauthorized();
  }
  return res;
};
