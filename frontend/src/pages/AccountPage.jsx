import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import './LoginPage.css';

export default function AccountPage() {
    const { user, fetchMe, logout, login } = useAuth();
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

    if (!user) return null;

    const resetForm = () => {
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
            const res = await fetch('/api/user', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    name: newName,
                    team: newTeam.toString(),
                    password: currentPassword
                })
            });

            if (res.ok) {
                // Feature: Popup and Auto Login
                alert('닉네임을 성공적으로 변경하였습니다');

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
                    alert('잘못된 비밀번호입니다');
                } else {
                    setError(errMsg);
                }
            }
        } catch (err) {
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
            const res = await fetch('/api/password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ currentPassword, newPassword })
            });
            if (res.ok) {
                setMsg('비밀번호가 변경되었습니다.');
                resetForm();
            } else {
                const data = await res.json();
                setError(data.message || '비밀번호 변경 실패');
            }
        } catch (err) {
            setError('서버 오류');
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async (e) => {
        e.preventDefault();
        if (!window.confirm('정말 계정을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.')) return;

        setLoading(true);
        setError('');
        try {
            const res = await fetch('/api/user', {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ password: currentPassword })
            });
            if (res.ok) {
                alert('계정이 삭제되었습니다.');
                await logout();
            } else {
                const data = await res.json();
                setError(data.message || '계정 삭제 실패');
            }
        } catch (err) {
            setError('서버 오류');
        } finally {
            setLoading(false);
        }
    };

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
                        <button type="submit" className="btn btn-primary" style={{ background: 'red', borderColor: 'red' }} disabled={loading}>계정 영구 삭제</button>
                    </form>
                )}

                <div style={{ marginTop: '20px', textAlign: 'center' }}>
                    <button className="btn btn-outline" style={{ width: 'auto', padding: '0 20px', height: '36px' }} onClick={() => nav('/game')}>게임으로 돌아가기</button>
                </div>
            </div>
        </div>
    );
}
