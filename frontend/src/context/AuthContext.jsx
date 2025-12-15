import React, { createContext, useContext, useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const nav = useNavigate();

    const fetchMe = async () => {
        try {
            const res = await fetch('/api/me', { credentials: 'include' });
            if (res.ok) {
                const data = await res.json();
                setUser(data);
            } else {
                setUser(null);
            }
        } catch (err) {
            console.error('Failed to fetch user', err);
            setUser(null);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchMe();
    }, []);

    const login = async (username, password) => {
        const form = new URLSearchParams();
        form.append('name', username);
        form.append('password', password);

        const res = await fetch('/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: form.toString(),
            credentials: 'include'
        });

        // Fix: Check URL to detect redirect to /login?error (which returns 200 OK)
        if (res.ok && !res.url.includes('error')) {
            await fetchMe();
            return true;
        }
        return false;
    };

    const logout = async () => {
        try {
            await fetch('/logout', { method: 'POST', credentials: 'include' });
            setUser(null);
            nav('/login?logout');
        } catch (err) {
            console.error('Logout failed', err);
        }
    };

    return (
        <AuthContext.Provider value={{ user, loading, login, logout, fetchMe }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
