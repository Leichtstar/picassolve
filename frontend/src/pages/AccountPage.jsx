import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import './LoginPage.css';

export default function AccountPage() {
    const { user } = useAuth();
    const [password, setPassword] = useState('');
    const [msg, setMsg] = useState('');

    const handleUpdate = async (e) => {
        e.preventDefault();
        // Implementation for password update
        setMsg('비밀번호 변경 기능은 준비 중입니다.');
    };

    if (!user) return null;

    return (
        <div className="login-container" style={{ background: 'var(--bg)' }}>
            <div className="card" style={{ margin: '40px auto', maxWidth: '500px' }}>
                <h2>내 계정</h2>
                <p>안녕하세요, <strong>{user.name}</strong>님!</p>
                <form onSubmit={handleUpdate} className="form">
                    <label>새 비밀번호</label>
                    <input type="password" value={password} onChange={e => setPassword(e.target.value)} />
                    <button type="submit">변경하기</button>
                </form>
                {msg && <p className="success">{msg}</p>}
            </div>
        </div>
    );
}
