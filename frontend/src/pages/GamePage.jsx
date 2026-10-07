import React, { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useGameSocket } from '../hooks/useGameSocket';

import GameHeader from '../game/GameHeader';
import GameCanvas from '../game/GameCanvas';
import UserList from '../game/UserList';
import RankingBoard from '../game/RankingBoard';
import GameChat from '../game/GameChat';

import './GamePage.css';

export default function GamePage() {
    const { user, loading, fetchMe } = useAuth();
    const nav = useNavigate();
    const canvasRef = useRef(null);

    useEffect(() => {
        if (!loading && !user) {
            nav('/login');
        }
    }, [user, loading, nav]);

    // Periodic session check while on the game page
    useEffect(() => {
        if (!user) return;
        const id = setInterval(() => {
            fetchMe();
        }, 60_000);
        return () => clearInterval(id);
    }, [user, fetchMe]);

    const onRemoteDraw = (data) => {
        if (!canvasRef.current) return;

        if (data.type === 'clear') {
            canvasRef.current.clearCanvas();
        } else if (data.type === 'undo') {
            if (data.actionId) {
                canvasRef.current.handleUndo(data.actionId);
            } else {
                canvasRef.current.handleUndo();
            }
        } else {
            canvasRef.current.drawSegment(data);
        }
    };

    const {
        connected,
        chatMessages,
        users,
        ranking,
        wordLen,
        mySecretWord,
        roleInfo,
        meDrawCooldownSec,
        actions
    } = useGameSocket(user, onRemoteDraw);

    if (loading || !user) return <div className="game-loading">로딩 중...</div>;

    return (
        <div className="game-layout">
            {!connected && (
                <div className="connection-banner" role="status">
                    서버와 연결이 끊겼습니다. 자동으로 다시 연결하는 중…
                </div>
            )}
            <GameHeader
                roleInfo={roleInfo}
                users={users}
                wordLen={wordLen}
                secretWord={mySecretWord}
                actions={actions}
                meDrawCooldownSec={meDrawCooldownSec}
            />

            <div className="game-main">
                <div className="left">
                    <GameCanvas
                        ref={canvasRef}
                        isDrawer={roleInfo.isDrawer}
                        onDrawStroke={actions.sendDraw}
                        onClear={actions.sendClear}
                        onUndo={actions.sendUndo}
                    />
                </div>

                <div className="right">
                    <GameChat
                        messages={chatMessages}
                        onSend={actions.sendChat}
                        isDrawer={roleInfo.isDrawer}
                        onReroll={actions.rerollWord}
                    />
                    <div className="side-panels">
                        <UserList users={users} />
                        <RankingBoard liveRanking={ranking} />
                    </div>
                </div>
            </div>
        </div>
    );
}
