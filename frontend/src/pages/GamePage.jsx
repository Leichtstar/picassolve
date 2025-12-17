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
    const { user, loading } = useAuth();
    const nav = useNavigate();
    const canvasRef = useRef(null);

    // Auth Guard
    useEffect(() => {
        if (!loading && !user) {
            nav('/login');
        }
    }, [user, loading, nav]);

    // Handle remote drawing commands
    const onRemoteDraw = (data) => {
        if (!canvasRef.current) return;

        if (data.type === 'clear') {
            canvasRef.current.clearCanvas();
        } else if (data.type === 'undo') {
            // Undo is handled complexly in legacy (redraw all). 
            // For now, let's trigger a clear and re-fetch logic if possible, 
            // OR let the hook handle history and call clear+redraw sequence.
            // Legacy code: server sends undo -> client removes stroke -> client clear -> client redraw.
            // Simplified: We treat Undo as a clear for now or wait for server to send full refresh.
            // Better: Legacy actually clears and redraws all segments locally. 
            // To strictly follow legacy, `useGameSocket` should track history.
            // For this step, we'll implement basic Clear handling.
            // Real undo needs local history in hook or canvas. 
            // Given time constraints, we rely on 'clear' being the fallback.
            if (data.actionId) {
                // Server directed specific undo. 
                // Without local history in Canvas, we can't do true undo yet.
                // Pass for now.
                canvasRef.current.handleUndo(data.actionId);
            } else {
                canvasRef.current.handleUndo(); // fallback
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
        actions
    } = useGameSocket(user, onRemoteDraw);

    if (loading || !user) return <div className="game-loading">로딩 중...</div>;

    return (
        <div className="game-layout">
            <GameHeader
                roleInfo={roleInfo}
                users={users}
                wordLen={wordLen}
                secretWord={mySecretWord}
                actions={actions}
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
