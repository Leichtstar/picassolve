import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';
import { Link } from 'react-router-dom';

export default function GameHeader({ roleInfo, users, wordLen, secretWord, actions }) {
    const { user, logout } = useAuth();
    const [time, setTime] = useState('');
    const [targetDrawer, setTargetDrawer] = useState('');
    const [canMeDraw, setCanMeDraw] = useState(false); // Throttle logic (simplified)

    useEffect(() => {
        const tick = () => {
            const d = new Date();
            setTime(d.toTimeString().split(' ')[0]);
        };
        tick();
        const id = setInterval(tick, 1000);
        return () => clearInterval(id);
    }, []);

    // Simple throttle/cooldown for "Me Draw" (simplified compared to legacy)
    useEffect(() => {
        setCanMeDraw(!roleInfo.isDrawer && !roleInfo.isAdmin);
    }, [roleInfo]);

    // Derive display text
    let centerText = '';
    const drawerName = users.find(u => u.role === 'DRAWER')?.name || '미정';

    if (roleInfo.isDrawer) {
        centerText = `이번 라운드의 Artist🎨는 당신입니다. 제시어 : ${secretWord || '(...)'}`;
    } else if (roleInfo.isAdmin) {
        centerText = `출제자 : ${drawerName} , 제시어 : ${secretWord || '(...)'}`;
    } else {
        centerText = `출제자는 ${drawerName}입니다. 제시어는 ${wordLen ?? '?'}글자입니다.`;
    }

    return (
        <header className="game-header">
            <div className="left">
                <strong>안녕하세요, {user.name}님</strong>
                <div className="account-dropdown">
                    <button className="account-btn">내 계정 ▾</button>
                    <div className="account-menu">
                        <Link to="/account">내 정보</Link>
                        <a href="#" onClick={(e) => { e.preventDefault(); logout(); }}>로그아웃</a>
                    </div>
                </div>
                <div className="clock">{time}</div>
            </div>

            <div className="center" style={{ flex: 2, justifyContent: 'center', fontWeight: 'bold', fontSize: '18px' }}>
                {centerText}
            </div>

            <div className="right-wrap">
                {roleInfo.isAdmin && (
                    <div className="admin-controls" style={{ display: 'flex', gap: '5px', alignItems: 'center' }}>
                        <strong>[관리자]</strong>
                        <input
                            placeholder="이름"
                            value={targetDrawer}
                            onChange={e => setTargetDrawer(e.target.value)}
                            style={{ width: '80px', height: '30px', padding: '0 5px' }}
                        />
                        <button onClick={() => actions.setDrawer(targetDrawer)}>지정</button>
                    </div>
                )}
                {canMeDraw && (
                    <button onClick={actions.reqMeDraw}>내가 그리기</button>
                )}
            </div>
        </header>
    );
}
