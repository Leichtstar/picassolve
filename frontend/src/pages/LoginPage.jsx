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
        <div className="login-container">
            <div className="login-card">
                <div className="brand-logo">PicasSolve</div>
                <h2>다시 오신 것을 환영합니다!</h2>
                <p className="subtitle">게임을 시작하려면 로그인하세요.</p>

                {msg && <div className="alert success">{msg}</div>}
                {error && <div className="alert error">{error}</div>}

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="username">아이디</label>
                        <input
                            type="text"
                            id="username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            placeholder="아이디를 입력하세요"
                            required
                        />
                    </div>
                    <div className="form-group">
                        <label htmlFor="password">비밀번호</label>
                        <input
                            type="password"
                            id="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="비밀번호를 입력하세요"
                            required
                        />
                    </div>
                    <button type="submit" className="login-btn">게임 입장</button>
                </form>
                <div className="login-footer">
                    계정이 없으신가요? <Link to="/register">회원가입</Link>
                </div>
            </div>
            <div className="login-hero">
                <img src="/img/img_main.png" alt="Picassolve Hero" />
            </div>
        </div>
    );
}
