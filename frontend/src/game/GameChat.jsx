import React, { useState, useEffect, useRef } from 'react';

export default function GameChat({ messages, onSend, isDrawer, onReroll }) {
    const [input, setInput] = useState('');
    const logRef = useRef(null);

    useEffect(() => {
        if (logRef.current) {
            logRef.current.scrollTop = logRef.current.scrollHeight;
        }
    }, [messages]);

    const handleSubmit = (e) => {
        e.preventDefault();
        if (!input.trim()) return;
        onSend(input);
        setInput('');
    };

    return (
        <div className="panel chat-panel">
            <h3>🗨️ 채팅</h3>
            <div className="chatlog" ref={logRef}>
                {messages.map((m, i) => (
                    <div key={i} className={m.system ? 'sys' : ''}>
                        <strong>{m.from}</strong> : {m.text}
                    </div>
                ))}
            </div>
            <form className="chatbox" onSubmit={handleSubmit}>
                <input
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="정답 또는 채팅"
                />
                <button type="submit">보내기</button>
            </form>
            {isDrawer && (
                <button
                    onClick={onReroll}
                    style={{ marginTop: '6px', width: '100%', background: '#fff', border: '1px solid #ccc', color: '#555' }}
                >
                    제시어 다시받기
                </button>
            )}
        </div>
    );
}
