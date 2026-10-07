import { useState, useEffect, useRef, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { backendUrl } from '../lib/backend';

const MAX_CHAT = 200;

const appendChat = (prev, data) => {
    const next = [...prev, data];
    return next.length > MAX_CHAT ? next.slice(-MAX_CHAT) : next;
};

const applyDrawBatch = (onDraw, body) => {
    const segs = typeof body === 'string' ? JSON.parse(body) : body;
    if (Array.isArray(segs) && onDraw) {
        for (const s of segs) onDraw(s);
    }
};

export const useGameSocket = (user, onDraw) => {
    const [connected, setConnected] = useState(false);
    const [chatMessages, setChatMessages] = useState([]);
    const [users, setUsers] = useState([]);
    const [ranking, setRanking] = useState([]);
    const [wordLen, setWordLen] = useState(null);
    const [mySecretWord, setMySecretWord] = useState(null);
    const [roleInfo, setRoleInfo] = useState({ isDrawer: false, isAdmin: false });
    const [meDrawCooldownSec, setMeDrawCooldownSec] = useState(0);

    const clientRef = useRef(null);
    const onDrawRef = useRef(onDraw);
    const cooldownTimerRef = useRef(null);

    useEffect(() => {
        onDrawRef.current = onDraw;
    }, [onDraw]);

    const startCooldown = useCallback((seconds) => {
        const sec = Math.max(0, Math.ceil(Number(seconds) || 0));
        if (sec <= 0) return;
        setMeDrawCooldownSec(sec);
        if (cooldownTimerRef.current) clearInterval(cooldownTimerRef.current);
        cooldownTimerRef.current = setInterval(() => {
            setMeDrawCooldownSec(prev => {
                if (prev <= 1) {
                    clearInterval(cooldownTimerRef.current);
                    cooldownTimerRef.current = null;
                    return 0;
                }
                return prev - 1;
            });
        }, 1000);
    }, []);

    useEffect(() => () => {
        if (cooldownTimerRef.current) clearInterval(cooldownTimerRef.current);
    }, []);

    const parseUserEntry = useCallback((str) => {
        const m = (str || '').match(/^(.+?)\s+\((ADMIN|DRAWER|PARTICIPANT)\)$/);
        return m ? { name: m[1], role: m[2] } : { name: str, role: null };
    }, []);

    const updateUsers = useCallback((rawList) => {
        const userList = (rawList || []).map(parseUserEntry);
        setUsers(userList);

        const me = userList.find(u => u.name === user?.name);
        if (me) {
            setRoleInfo({
                isDrawer: (me.role === 'DRAWER'),
                isAdmin: (me.role === 'ADMIN')
            });
        }
    }, [parseUserEntry, user?.name]);

    useEffect(() => {
        if (!user?.name) return;

        const client = new Client({
            webSocketFactory: () => new SockJS(backendUrl('/ws')),
            reconnectDelay: 3000,
            onConnect: () => {
                setConnected(true);

                client.subscribe('/topic/chat', (msg) => {
                    const data = JSON.parse(msg.body);
                    setChatMessages(prev => appendChat(prev, data));
                });

                client.subscribe('/topic/users', (msg) => {
                    updateUsers(JSON.parse(msg.body));
                });

                client.subscribe('/topic/scoreboard', (msg) => {
                    setRanking(JSON.parse(msg.body));
                });

                client.subscribe('/topic/wordlen', (msg) => {
                    setWordLen(parseInt(msg.body, 10));
                });

                client.subscribe('/topic/draw', (msg) => {
                    const drawData = JSON.parse(msg.body);
                    if (onDrawRef.current) onDrawRef.current(drawData);
                });

                client.subscribe('/topic/draw-batch', (msg) => {
                    applyDrawBatch(onDrawRef.current, msg.body);
                });

                client.subscribe('/topic/canvas/clear', () => {
                    if (onDrawRef.current) onDrawRef.current({ type: 'clear' });
                });

                client.subscribe('/topic/undo', (msg) => {
                    const { actionId } = JSON.parse(msg.body);
                    if (onDrawRef.current) onDrawRef.current({ type: 'undo', actionId });
                });

                client.subscribe('/user/queue/users', (msg) => updateUsers(JSON.parse(msg.body)));
                client.subscribe('/user/queue/scoreboard', (msg) => setRanking(JSON.parse(msg.body)));
                client.subscribe('/user/queue/word', (msg) => setMySecretWord(msg.body || null));
                client.subscribe('/user/queue/wordlen', (msg) => setWordLen(parseInt(msg.body, 10)));

                client.subscribe('/user/queue/errors', (msg) => {
                    const text = msg.body || '';
                    setChatMessages(prev => appendChat(prev, { from: 'SYSTEM', text, system: true }));
                    const m = text.match(/(\d+)\s*초/);
                    if (m) startCooldown(parseInt(m[1], 10));
                });

                client.subscribe('/user/queue/draw', (msg) => {
                    if (onDrawRef.current) onDrawRef.current(JSON.parse(msg.body));
                });

                client.subscribe('/user/queue/draw-batch', (msg) => {
                    applyDrawBatch(onDrawRef.current, msg.body);
                });

                client.subscribe('/user/queue/canvas/clear', () => {
                    if (onDrawRef.current) onDrawRef.current({ type: 'clear' });
                });

                client.subscribe('/user/queue/force-logout', (msg) => {
                    let allowSid = '';
                    try { allowSid = JSON.parse(msg.body).allowSid; } catch (_) { /* ignore */ }
                    const mySid = user?.sid;
                    // Only leave if this tab is the kicked (old) session
                    if (mySid && allowSid && mySid === allowSid) {
                        return;
                    }
                    window.location.href = '/login?logout';
                });

                client.publish({ destination: '/app/state.sync', body: '{}' });
            },
            onDisconnect: () => {
                setConnected(false);
            },
            onWebSocketClose: () => {
                setConnected(false);
            },
            onStompError: (frame) => {
                console.error('Broker error:', frame.headers['message']);
                setConnected(false);
            }
        });

        client.activate();
        clientRef.current = client;

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
                clientRef.current = null;
            }
            setConnected(false);
        };
    }, [user?.name, user?.sid, updateUsers, startCooldown]);

    const sendChat = (text) => {
        if (!clientRef.current || !connected || !user?.name) return;
        clientRef.current.publish({
            destination: '/app/chat.send',
            body: JSON.stringify({ text })
        });
    };

    const sendDraw = (payload) => {
        if (!clientRef.current || !connected) return;
        clientRef.current.publish({
            destination: '/app/draw.stroke',
            body: JSON.stringify(payload)
        });
    };

    const sendClear = () => {
        if (!clientRef.current || !connected) return;
        clientRef.current.publish({ destination: '/app/canvas.clear', body: '{}' });
    };

    const sendUndo = () => {
        if (!clientRef.current || !connected) return;
        clientRef.current.publish({ destination: '/app/draw.undo', body: '{}' });
    };

    const setDrawer = (targetName) => {
        if (!clientRef.current || !connected) return;
        clientRef.current.publish({
            destination: '/app/admin.setDrawer',
            body: JSON.stringify({ name: targetName })
        });
    };

    const rerollWord = () => {
        if (!clientRef.current || !connected) return;
        clientRef.current.publish({ destination: '/app/word.reroll', body: '{}' });
    };

    const reqMeDraw = () => {
        if (!clientRef.current || !connected) return;
        if (meDrawCooldownSec > 0) return;
        clientRef.current.publish({ destination: '/app/drawer.me', body: '{}' });
    };

    return {
        connected,
        chatMessages,
        users,
        ranking,
        wordLen,
        mySecretWord,
        roleInfo,
        meDrawCooldownSec,
        actions: {
            sendChat,
            sendDraw,
            sendClear,
            sendUndo,
            setDrawer,
            rerollWord,
            reqMeDraw
        }
    };
};
