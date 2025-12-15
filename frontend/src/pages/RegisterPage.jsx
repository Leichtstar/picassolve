import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import './LoginPage.css'; // Share styles

export default function RegisterPage() {
    const nav = useNavigate();
    const [formData, setFormData] = useState({ name: '', password: '', confirm: '' });
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
            form.append('team', '1'); // Default team required by backend

            const res = await fetch('/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: form
            });

            // Backend redirects to /login on success
            if (res.ok && (res.url.includes('login') || res.redirected)) {
                nav('/login?registered=true');
            } else {
                // If it returns the register page HTML (200 OK), it means failure
                setError('회원가입 실패 (이미 존재하는 이름일 수 있습니다).');
            }
        } catch (err) {
            setError('서버 오류가 발생했습니다.');
        }
    };

    return (
        <div className="login-container">
            <div className="login-card">
                <div className="brand-logo">PicasSolve</div>
                <h2>계정 만들기</h2>
                <p className="subtitle">새로운 여정을 시작해보세요.</p>

                {error && <div className="alert error">{error}</div>}

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label>이름</label>
                        <input type="text" value={formData.name} onChange={e => setFormData({ ...formData, name: e.target.value })} required />
                    </div>
                    <div className="form-group">
                        <label>비밀번호</label>
                        <input type="password" value={formData.password} onChange={e => setFormData({ ...formData, password: e.target.value })} required />
                    </div>
                    <div className="form-group">
                        <label>비밀번호 확인</label>
                        <input type="password" value={formData.confirm} onChange={e => setFormData({ ...formData, confirm: e.target.value })} required />
                    </div>
                    <button type="submit" className="login-btn">회원가입</button>
                </form>
                <div className="login-footer">
                    이미 계정이 있으신가요? <Link to="/login">로그인</Link>
                </div>
            </div>
            <div className="login-hero">
                <img src="/img/img_main.png" alt="Picassolve Hero" />
            </div>
        </div>
    );
}
