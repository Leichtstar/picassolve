import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import './LoginPage.css';

export default function AccountPage() {
    const { user } = useAuth();
    const nav = useNavigate();

    // Form State
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    // UI State
    const [msg, setMsg] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    if (!user) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setMsg('');
        setError('');

        if (newPassword !== confirmPassword) {
            setError('새 비밀번호가 일치하지 않습니다.');
            return;
        }

        setLoading(true);
        try {
            const res = await fetch('/api/password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    currentPassword,
                    newPassword
                })
            });

            if (res.ok) {
                setMsg('비밀번호가 성공적으로 변경되었습니다.');
                setCurrentPassword('');
                setNewPassword('');
                setConfirmPassword('');
            } else {
                // Backend might return error text
                const text = await res.text();
                setError(text || '비밀번호 변경에 실패했습니다. 현재 비밀번호를 확인해주세요.');
            }
        } catch (err) {
            setError('서버 통신 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-shell">
            <div className="auth-card auth-card--stack">
                <div className="auth-header">
                    <h1>비밀번호 변경</h1>
                    <p>현재 계정: {user.name}</p>
                </div>

                {msg && (
                    <div className="alert alert-success">
                        <span className="alert-icon">✓</span>
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
                        className="input"
                        type="password"
                        placeholder="현재 비밀번호"
                        value={currentPassword}
                        onChange={e => setCurrentPassword(e.target.value)}
                        required
                    />
                    <input
                        className="input"
                        type="password"
                        placeholder="새 비밀번호"
                        value={newPassword}
                        onChange={e => setNewPassword(e.target.value)}
                        required
                    />
                    <input
                        className="input"
                        type="password"
                        placeholder="새 비밀번호 확인"
                        value={confirmPassword}
                        onChange={e => setConfirmPassword(e.target.value)}
                        required
                    />

                    <div className="actions">
                        <button type="button" className="btn btn-outline" onClick={() => nav('/game')}>
                            게임으로 돌아가기
                        </button>
                        <button type="submit" className="btn btn-primary" disabled={loading}>
                            {loading ? '변경 중...' : '비밀번호 변경'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
