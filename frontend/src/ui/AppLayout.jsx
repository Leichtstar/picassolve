import React from 'react';
import { Outlet, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import '../styles/base.css'; // Will create this later
import './AppLayout.css'; // Will create this later

export default function AppLayout() {
    const location = useLocation();
    const { user, logout } = useAuth();
    const isGame = location.pathname.startsWith('/game');

    return (
        <div className="app-shell">
            <header className="topbar">
                <div className="brand">PicasSolve</div>
                <nav>
                    <Link to="/game">게임</Link>
                    {!user && <Link to="/login">로그인</Link>}
                    {!user && <Link to="/register">회원가입</Link>}
                    {user && (
                        <>
                            <Link to="/account" title={user.name}>내 계정</Link>
                            <a href="#" onClick={(e) => { e.preventDefault(); logout(); }}>로그아웃</a>
                        </>
                    )}
                </nav>
            </header>
            <main className={isGame ? 'no-padding' : ''}>
                <Outlet />
            </main>
        </div>
    );
}
