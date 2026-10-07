import React, { useState, useEffect } from 'react';
import { useAuth } from '../context/AuthContext';

export default function GameHeader({ roleInfo, users, wordLen, secretWord, actions, meDrawCooldownSec = 0 }) {
    const { user } = useAuth();
    const [time, setTime] = useState('');
    const [targetDrawer, setTargetDrawer] = useState('');

    useEffect(() => {
        const tick = () => {
            const d = new Date();
            setTime(d.toTimeString().split(' ')[0]);
        };
        tick();
        const id = setInterval(tick, 1000);
        return () => clearInterval(id);
    }, []);

    const canMeDraw = !roleInfo.isDrawer && !roleInfo.isAdmin;
    const coolingDown = meDrawCooldownSec > 0;

    const drawerName = users.find(u => u.role === 'DRAWER')?.name || '미정';

    let roundMsg = '';
    let wordMsg = '';
    if (roleInfo.isDrawer) {
        roundMsg = '이번 라운드의 Artist🎨는 당신입니다.';
        wordMsg = `제시어 : ${secretWord || '(...)'}`;
    } else if (roleInfo.isAdmin) {
        roundMsg = `출제자 : ${drawerName}`;
        wordMsg = `제시어 : ${secretWord || '(...)'}`;
    } else {
        roundMsg = `출제자는 ${drawerName}입니다.`;
        wordMsg = `제시어 : ${wordLen ?? '?'}글자`;
    }

    return (
        <header className="game-header">
            <div className="left">
                <strong>안녕하세요, {user.name}님</strong>
                <div className="clock">{time}</div>
            </div>

            <div className="center" style={{ flex: 2, justifyContent: 'center' }}>
                <span className="round-msg">{roundMsg}</span>
                <span className="word-msg">{wordMsg}</span>
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
                    <button
                        onClick={actions.reqMeDraw}
                        disabled={coolingDown}
                        title={coolingDown ? `${meDrawCooldownSec}초 후 가능` : '지금 내가 그리기 가능'}
                    >
                        {coolingDown ? `내가 그리기 (${meDrawCooldownSec}초)` : '내가 그리기'}
                    </button>
                )}
            </div>
        </header>
    );
}
