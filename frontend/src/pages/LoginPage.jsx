import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import './LoginPage.css';

export default function LoginPage() {
    const { login, user } = useAuth();
    const nav = useNavigate();
    const location = useLocation();
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [msg, setMsg] = useState('');

    useEffect(() => {
        if (user) nav('/game');
        const params = new URLSearchParams(location.search);
        if (params.has('logout')) setMsg('정상적으로 로그아웃되었습니다.');
        if (params.has('error')) setError('아이디 또는 비밀번호가 일치하지 않습니다.');
    }, [user, location, nav]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setMsg('');
        const success = await login(username, password);
        if (success) {
            nav('/game');
        } else {
            setError('로그인에 실패했습니다. 아이디와 비밀번호를 확인해주세요.');
        }
    };

    return (
        <div className="auth-shell">
            <div className="auth-card auth-card--split">
                <div className="auth-illustration">
                    <img src="/img/img_main.png" alt="PicasSolve 메인 일러스트" className="auth-illustration__image" />
                </div>
                <div className="auth-panel">
                    <div className="auth-header">
                        <h1>🖌️ 피카-솔브 로그인</h1>
                        <p>💻 등록된 이름과 비밀번호로 접속하세요.</p>
                    </div>

                    {msg && (
                        <div className="alert alert-info">
                            <span className="alert-icon">i</span>
                            <span>{msg}</span>
                        </div>
                    )}
                    {error && (
                        <div className="alert alert-error">
                            <span className="alert-icon">!</span>
                            <span>{error}</span>
                        </div>
                    )}

                    <form onSubmit={handleSubmit} className="form">
                        <input
                            id="username"
                            name="name"
                            className="input"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            placeholder="이름"
                            required
                            autoFocus
                        />
                        <input
                            id="password"
                            name="password"
                            className="input"
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="비밀번호"
                            required
                        />
                        <button type="submit" className="btn btn-primary">게임 입장</button>
                    </form>

                    <p className="link">
                        아직 계정이 없다면 <Link to="/register">회원가입</Link>
                    </p>

                    <div className="auth-footer">
                        <span className="auth-footer__credit">Developed by_DevstarQ</span>
                        <a
                            className="btn btn-primary btn-small auth-footer__burger"
                            href="https://link.kakaopay.com/__/W2dVVoX"
                            target="_blank"
                            rel="noreferrer"
                        >
                            Buy me a burger🍔
                        </a>
                    </div>
                </div>
            </div>
        </div>
    );
}
