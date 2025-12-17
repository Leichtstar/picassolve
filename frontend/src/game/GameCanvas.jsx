import React, { useRef, useState, useEffect, useImperativeHandle, forwardRef } from 'react';

const GameCanvas = forwardRef(({ isDrawer, onDrawStroke, onClear, onUndo }, ref) => {
    const canvasRef = useRef(null);
    const [color, setColor] = useState('#000000');
    const [width, setWidth] = useState(3);
    const [mode, setMode] = useState('pen'); // 'pen' | 'eraser'

    // History state for Undo/Redraw
    const history = useRef([]); // Array of { id: actionId, segs: [] }
    const currentRemoteAction = useRef(null); // { id, segs }

    // Local state for drawing interactions
    const isDrawing = useRef(false);
    const prevPos = useRef(null);
    const currentActionId = useRef(null);
    const isNewStroke = useRef(false);

    // Optimization: Offscreen canvas for baking old history
    const mergedCanvasRef = useRef(null);
    const MAX_HISTORY = 10;

    useEffect(() => {
        // Initialize offscreen canvas
        if (!mergedCanvasRef.current) {
            const oc = document.createElement('canvas');
            oc.width = 770;
            oc.height = 600;
            mergedCanvasRef.current = oc;
        }
    }, []);

    // Expose methods
    useImperativeHandle(ref, () => ({
        drawSegment: (data) => {
            const ctx = canvasRef.current.getContext('2d');

            // 1. Update History
            if (data.newStroke || !currentRemoteAction.current || currentRemoteAction.current.id !== data.actionId) {
                // Check limit
                if (history.current.length >= MAX_HISTORY) {
                    // Bake the oldest action into mergedCanvas
                    const oldest = history.current.shift();
                    const mCtx = mergedCanvasRef.current.getContext('2d');
                    oldest.segs.forEach(s => drawLine(mCtx, s));
                }

                currentRemoteAction.current = { id: data.actionId, segs: [] };
                history.current.push(currentRemoteAction.current);
            }
            currentRemoteAction.current.segs.push(data);

            // 2. Draw direct (interactive)
            drawLine(ctx, data);
        },
        clearCanvas: () => {
            const cvs = canvasRef.current;
            const ctx = cvs.getContext('2d');
            ctx.clearRect(0, 0, cvs.width, cvs.height);
            history.current = [];
            currentRemoteAction.current = null;

            // Clear merged canvas too
            if (mergedCanvasRef.current) {
                const mCtx = mergedCanvasRef.current.getContext('2d');
                mCtx.clearRect(0, 0, mergedCanvasRef.current.width, mergedCanvasRef.current.height);
            }
        },
        handleUndo: (targetActionId) => {
            let removed = false;
            if (targetActionId) {
                const idx = history.current.findIndex(a => a.id === targetActionId);
                if (idx !== -1) {
                    history.current.splice(idx, 1);
                    removed = true;
                }
            } else {
                if (history.current.length > 0) {
                    history.current.pop();
                    removed = true;
                }
            }

            if (!removed) return;

            // Redraw: Merged + History
            const cvs = canvasRef.current;
            const ctx = cvs.getContext('2d');
            ctx.clearRect(0, 0, cvs.width, cvs.height);

            // 1. Draw baked background
            if (mergedCanvasRef.current) {
                ctx.drawImage(mergedCanvasRef.current, 0, 0);
            }

            // 2. Draw active history
            history.current.forEach(action => {
                action.segs.forEach(seg => drawLine(ctx, seg));
            });

            currentRemoteAction.current = history.current.length > 0 ? history.current[history.current.length - 1] : null;
        }
    }));

    const getPos = (e) => {
        const cvs = canvasRef.current;
        const rect = cvs.getBoundingClientRect();
        let clientX, clientY;

        if (e.changedTouches) {
            clientX = e.changedTouches[0].clientX;
            clientY = e.changedTouches[0].clientY;
        } else {
            clientX = e.clientX;
            clientY = e.clientY;
        }

        return {
            x: (clientX - rect.left) * (cvs.width / rect.width),
            y: (clientY - rect.top) * (cvs.height / rect.height)
        };
    };

    const drawLine = (ctx, { x1, y1, x2, y2, color, width, mode }) => {
        ctx.save();
        if (mode === 'eraser') {
            ctx.globalCompositeOperation = 'destination-out';
            ctx.strokeStyle = 'rgba(0,0,0,1)';
        } else {
            ctx.globalCompositeOperation = 'source-over';
            ctx.strokeStyle = color;
        }
        ctx.lineWidth = width;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.lineTo(x2, y2);
        ctx.stroke();
        ctx.restore();
    };

    const startDrawing = (e) => {
        if (!isDrawer) return;
        if (e.type === 'mousedown' && e.button !== 0) return;

        isDrawing.current = true;
        prevPos.current = getPos(e);
        currentActionId.current = Date.now().toString(36) + Math.random().toString(36).substr(2, 5);
        isNewStroke.current = true;
    };

    const stopDrawing = () => {
        isDrawing.current = false;
        prevPos.current = null;
        currentActionId.current = null;
        isNewStroke.current = false;
    };

    const draw = (e) => {
        if (!isDrawing.current || !isDrawer) return;
        e.preventDefault();

        const currPos = getPos(e);

        const payload = {
            x1: prevPos.current.x,
            y1: prevPos.current.y,
            x2: currPos.x,
            y2: currPos.y,
            color: color,
            width: width,
            mode: mode,
            actionId: currentActionId.current,
            newStroke: isNewStroke.current
        };

        onDrawStroke(payload);

        prevPos.current = currPos;
        isNewStroke.current = false;
    };

    return (
        <div className="board-stack">
            <canvas
                ref={canvasRef}
                width={770}
                height={600}
                className="game-canvas"
                onMouseDown={startDrawing}
                onMouseUp={stopDrawing}
                onMouseLeave={stopDrawing}
                onMouseMove={draw}
                onTouchStart={startDrawing}
                onTouchEnd={stopDrawing}
                onTouchCancel={stopDrawing}
                onTouchMove={draw}
            />
            <div className="tools">
                <input
                    type="color"
                    value={color}
                    onChange={(e) => setColor(e.target.value)}
                    disabled={!isDrawer}
                />
                <input
                    type="range"
                    min="1"
                    max="20"
                    value={width}
                    onChange={(e) => setWidth(parseInt(e.target.value))}
                    disabled={!isDrawer}
                />
                <button
                    className={mode === 'eraser' ? 'active' : ''}
                    onClick={() => setMode(mode === 'eraser' ? 'pen' : 'eraser')}
                    disabled={!isDrawer}
                    title="지우개"
                >🩹</button>
                <button onClick={onUndo} disabled={!isDrawer} title="실행취소">↶</button>
                <button onClick={onClear} disabled={!isDrawer} title="전체 지우기">🗑️</button>
            </div>
        </div>
    );
});

export default GameCanvas;
