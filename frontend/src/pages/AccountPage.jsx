import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useAlert } from '../context/AlertContext';
import { useNavigate } from 'react-router-dom';
import { backendFetch } from '../lib/backend';
import './LoginPage.css';

export default function AccountPage() {
    const { user, loading: authLoading, logout, login } = useAuth();
    const { success: alertSuccess, error: alertError } = useAlert();
    const nav = useNavigate();

    // Tab State: 'PROFILE' | 'PASSWORD' | 'DELETE'
    const [mode, setMode] = useState('PROFILE');

    // Forms
    const [newName, setNewName] = useState('');
    const [newTeam, setNewTeam] = useState('');
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');

    // Feedback
    const [msg, setMsg] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);

    // Auth Guard
    React.useEffect(() => {
        if (!authLoading && !user) {
            nav('/login');
        }
    }, [user, authLoading, nav]);

    const resetForm = () => {
        if (!user) return;
        setNewName(user.name); // Prefill current name
        setNewTeam(user.team || '0'); // Prefill current team
        setCurrentPassword('');
        setNewPassword('');
        setConfirmPassword('');
        setError('');
        setMsg('');
    };

    // Initialize form data when tab changes or user loads
    React.useEffect(() => {
        if (!user) return;
        if (mode === 'PROFILE') {
            setNewName(user.name);
            setNewTeam(user.team || '0');
        }
    }, [mode, user]);

    const handleTabChange = (m) => {
        setMode(m);
        // Form reset handled by effect or manually if needed
        setError('');
        setMsg('');
    };

    const handleProfileUpdate = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError('');
        setMsg('');

        try {
            const res = await backendFetch('/api/user', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: newName,
                    team: newTeam.toString(),
                    password: currentPassword
                }),
                credentials: 'include'
            });

            if (res.ok) {
                // Feature: Popup and Auto Login
                alertSuccess('닉네임을 성공적으로 변경하였습니다');

                // Re-login with new name and CURRENT password
                const loginSuccess = await login(newName, currentPassword);
                if (loginSuccess) {
                    nav('/game');
                } else {
                    setError('변경은 완료되었으나 로그인에 실패했습니다. 다시 로그인해주세요.');
                    logout();
                }
            } else {
                const data = await res.json();
                const errMsg = data.message || '변경 실패';

                // Specific handling for password mismatch
                if (errMsg.includes('비밀번호')) {
                    alertError('잘못된 비밀번호입니다');
                } else {
                    setError(errMsg);
                }
            }
        } catch {
            setError('서버 오류');
        } finally {
            setLoading(false);
        }
    };

    const handlePasswordUpdate = async (e) => {
        e.preventDefault();
        if (newPassword !== confirmPassword) {
            return setError('새 비밀번호가 일치하지 않습니다.');
        }
        setLoading(true);
        setError('');
        setMsg('');

        try {
            const res = await backendFetch('/api/password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentPassword, newPassword }),
                credentials: 'include'
            });
            if (res.ok) {
                alertSuccess('비밀번호가 변경되었습니다');
                resetForm();
            } else {
                const data = await res.json();
                const errMsg = data.message || '비밀번호 변경 실패';
                if (errMsg.includes('비밀번호')) {
                    alertError('잘못된 비밀번호입니다');
                } else {
                    setError(errMsg);
                }
            }
        } catch {
            setError('서버 오류');
        } finally {
            setLoading(false);
        }
    };

    // Delete confirmation state
    const [deleteConfirmed, setDeleteConfirmed] = React.useState(false);

    const handleDelete = async (e) => {
        e.preventDefault();

        // First click: show warning
        if (!deleteConfirmed) {
            alertError('정말 삭제하시려면 버튼을 한 번 더 클릭하세요!');
            setDeleteConfirmed(true);
            // Reset after 5 seconds
            setTimeout(() => setDeleteConfirmed(false), 5000);
            return;
        }

        // Second click: proceed with deletion

        setLoading(true);
        setError('');
        try {
            const res = await backendFetch('/api/user', {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password: currentPassword }),
                credentials: 'include'
            });
            if (res.ok) {
                alertSuccess(`${user.name} 계정 삭제에 성공하였습니다.`);
                await logout();
            } else {
                const data = await res.json();
                const errMsg = data.message || '계정 삭제 실패';
                if (errMsg.includes('비밀번호')) {
                    alertError('잘못된 비밀번호입니다');
                } else {
                    setError(errMsg);
                }
            }
        } catch {
            setError('서버 오류');
        } finally {
            setLoading(false);
        }
    };

    if (authLoading) return <div className="auth-shell">로딩 중...</div>;
    if (!user) return <div className="auth-shell" />;

    return (
        <div className="auth-shell">
            <div className="auth-card auth-card--stack" style={{ maxWidth: '600px' }}>
                <div className="auth-header">
                    <h1>내 계정 관리</h1>
                    <p>현재 계정: <strong>{user.name}</strong></p>
                </div>

                <div className="actions" style={{ marginBottom: '20px', borderBottom: '1px solid #eee', paddingBottom: '10px' }}>
                    <button className={`btn ${mode === 'PROFILE' ? 'btn-primary' : 'btn-outline'}`} onClick={() => handleTabChange('PROFILE')}>닉네임 변경</button>
                    <button className={`btn ${mode === 'PASSWORD' ? 'btn-primary' : 'btn-outline'}`} onClick={() => handleTabChange('PASSWORD')}>비밀번호 변경</button>
                    <button className={`btn ${mode === 'DELETE' ? 'btn-primary' : 'btn-outline'}`} onClick={() => handleTabChange('DELETE')} style={{ borderColor: 'red', color: mode === 'DELETE' ? 'white' : 'red', background: mode === 'DELETE' ? 'red' : '' }}>계정 삭제</button>
                </div>

                {msg && <div className="alert alert-success"><span className="alert-icon">✓</span><span>{msg}</span></div>}
                {error && <div className="alert alert-error"><span className="alert-icon">!</span><span>{error}</span></div>}

                {mode === 'PROFILE' && (
                    <form onSubmit={handleProfileUpdate} className="form">
                        <div style={{ display: 'flex', gap: '10px' }}>
                            <div style={{ flex: 8 }}>
                                <input
                                    className="input"
                                    placeholder="익명"
                                    value={newName}
                                    onChange={e => setNewName(e.target.value)}
                                    required
                                />
                            </div>
                            <div style={{ flex: 2, display: 'flex', alignItems: 'center', gap: '5px' }}>
                                <span style={{ fontWeight: 'bold', fontSize: '14px', whiteSpace: 'nowrap' }}>팀</span>
                                <input
                                    className="input"
                                    type="number"
                                    min="0"
                                    value={newTeam}
                                    onChange={e => setNewTeam(e.target.value)}
                                    required
                                />
                            </div>
                        </div>
                        <input className="input" type="password" placeholder="현재 비밀번호 (확인용)" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} required />
                        <button type="submit" className="btn btn-primary" disabled={loading}>변경하기</button>
                    </form>
                )}

                {mode === 'PASSWORD' && (
                    <form onSubmit={handlePasswordUpdate} className="form">
                        <input className="input" type="password" placeholder="현재 비밀번호" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} required />
                        <input className="input" type="password" placeholder="새 비밀번호" value={newPassword} onChange={e => setNewPassword(e.target.value)} required />
                        <input className="input" type="password" placeholder="새 비밀번호 확인" value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} required />
                        <button type="submit" className="btn btn-primary" disabled={loading}>변경하기</button>
                    </form>
                )}

                {mode === 'DELETE' && (
                    <form onSubmit={handleDelete} className="form">
                        <p style={{ color: 'red', fontSize: '14px' }}>⚠️ 계정 삭제 시 모든 데이터가 영구적으로 삭제됩니다.</p>
                        <input className="input" type="password" placeholder="현재 비밀번호를 입력하여 확인" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} required />
                        <button type="submit" className="btn btn-primary" style={{ background: deleteConfirmed ? '#8B0000' : 'red', borderColor: deleteConfirmed ? '#8B0000' : 'red' }} disabled={loading}>
                            {deleteConfirmed ? '⚠️ 확인! 클릭하면 삭제됩니다' : '계정 영구 삭제'}
                        </button>
                    </form>
                )}

                <div style={{ marginTop: '20px', textAlign: 'center' }}>
                    <button className="btn btn-outline" style={{ width: 'auto', padding: '0 20px', height: '36px' }} onClick={() => nav('/game')}>게임으로 돌아가기</button>
                </div>

                <div className="auth-footer" style={{ marginTop: '16px' }}>
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
    );
}
