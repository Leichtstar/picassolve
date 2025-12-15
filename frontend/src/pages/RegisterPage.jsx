import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { backendFetch } from '../lib/backend';
import './LoginPage.css'; // Share styles

export default function RegisterPage() {
    const nav = useNavigate();
    const [formData, setFormData] = useState({ name: '', password: '', confirm: '', team: '0' });
    const [error, setError] = useState('');

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (formData.password !== formData.confirm) {
            return setError('비밀번호가 일치하지 않습니다.');
        }

        try {
            const form = new URLSearchParams();
            form.append('name', formData.name);
            form.append('password', formData.password);
            form.append('team', formData.team);

            const res = await backendFetch('/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: form.toString(),
                credentials: 'include'
            });

            // Backend redirects to /login on success
            if (res.ok && (res.url.includes('login') || res.redirected)) {
                nav('/login?registered=true');
            } else {
                // If it returns the register page HTML (200 OK), it means failure
                setError('회원가입 실패 (이미 존재하는 이름일 수 있습니다).');
            }
        } catch {
            setError('서버 오류가 발생했습니다.');
        }
    };

    return (
        <div className="auth-shell">
            <div className="auth-card auth-card--stack">
                <div className="auth-header">
                    <h1>회원가입</h1>
                    <p>게임에 참여할 이름과 비밀번호, 팀 번호를 입력하세요.</p>
                </div>

                {error && (
                    <div className="alert alert-error">
                        <span className="alert-icon">!</span>
                        <span>{error}</span>
                    </div>
                )}

                <form onSubmit={handleSubmit} className="form">
                    <input
                        className="input"
                        placeholder="이름"
                        value={formData.name}
                        onChange={e => setFormData({ ...formData, name: e.target.value })}
                        required
                        autoFocus
                    />
                    <input
                        className="input"
                        type="password"
                        placeholder="비밀번호"
                        value={formData.password}
                        onChange={e => setFormData({ ...formData, password: e.target.value })}
                        required
                    />
                    <input
                        className="input"
                        type="password"
                        placeholder="비밀번호 확인"
                        value={formData.confirm}
                        onChange={e => setFormData({ ...formData, confirm: e.target.value })}
                        required
                    />
                    <input
                        className="input"
                        type="number"
                        min="0"
                        placeholder="팀 번호"
                        value={formData.team || ''}
                        onChange={e => setFormData({ ...formData, team: e.target.value })}
                        required
                    />

                    <div className="actions">
                        <button type="button" className="btn btn-outline" onClick={() => nav('/login')}>돌아가기</button>
                        <button type="submit" className="btn btn-primary">회원가입</button>
                    </div>
                </form>
            </div>
        </div>
    );
}
