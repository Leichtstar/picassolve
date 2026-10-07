import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { backendFetch, setUnauthorizedHandler } from '../lib/backend';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const nav = useNavigate();
    const clearingRef = useRef(false);

    const clearSession = useCallback((redirect = true) => {
        if (clearingRef.current) return;
        clearingRef.current = true;
        setUser(null);
        document.cookie = 'JSESSIONID=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;';
        if (redirect) {
            nav('/login?error');
        }
        setTimeout(() => { clearingRef.current = false; }, 500);
    }, [nav]);

    const fetchMe = useCallback(async () => {
        try {
            const res = await backendFetch('/api/me', { credentials: 'include' });
            if (res.ok) {
                const data = await res.json();
                setUser(data);
                return data;
            }
            setUser(null);
            return null;
        } catch (err) {
            console.error('Failed to fetch user', err);
            setUser(null);
            return null;
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        setUnauthorizedHandler(() => clearSession(true));
        fetchMe();
        return () => setUnauthorizedHandler(null);
    }, [fetchMe, clearSession]);

    const login = async (username, password) => {
        const form = new URLSearchParams();
        form.append('name', username);
        form.append('password', password);

        const res = await backendFetch('/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest'
            },
            body: form.toString(),
            credentials: 'include'
        });

        if (res.ok && !res.url.includes('error')) {
            await fetchMe();
            return true;
        }
        return false;
    };

    const logout = async () => {
        try {
            await backendFetch('/logout', {
                method: 'POST',
                headers: {
                    'X-Requested-With': 'XMLHttpRequest'
                },
                credentials: 'include'
            });

            document.cookie = 'JSESSIONID=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;';
            setUser(null);
            nav('/login?logout');
        } catch (err) {
            console.error('Logout failed', err);
        }
    };

    return (
        <AuthContext.Provider value={{ user, loading, login, logout, fetchMe, clearSession }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
