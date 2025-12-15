import { useState, useEffect, useRef, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

export const useGameSocket = (user, onDraw) => {
    const [connected, setConnected] = useState(false);
    const [chatMessages, setChatMessages] = useState([]);
    const [users, setUsers] = useState([]); // [{name, role}]
    const [ranking, setRanking] = useState([]);
    const [wordLen, setWordLen] = useState(null);
    const [mySecretWord, setMySecretWord] = useState(null);
    const [roleInfo, setRoleInfo] = useState({ isDrawer: false, isAdmin: false });

    const clientRef = useRef(null);
    const onDrawRef = useRef(onDraw);

    useEffect(() => {
        onDrawRef.current = onDraw;
    }, [onDraw]);

    // Helper to parse "Name (ROLE)" string from backend
    const parseUserEntry = useCallback((str) => {
        const m = (str || '').match(/^(.+?)\s+\((ADMIN|DRAWER|PARTICIPANT)\)$/);
        return m ? { name: m[1], role: m[2] } : { name: str, role: null };
    }, []);

    // Helper to update users and derived roles
    const updateUsers = useCallback((rawList) => {
        // Parse the list of strings into objects
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

        const socket = new SockJS('http://localhost:8099/ws');
        const client = new Client({
            webSocketFactory: () => socket,
            // debug: (str) => console.log('[STOMP]', str),
            onConnect: () => {
                setConnected(true);
                // console.log('STOMP Connected');

                // 1. Subscribe to Broadcasts
                client.subscribe('/topic/chat', (msg) => {
                    const data = JSON.parse(msg.body);
                    setChatMessages(prev => [...prev, data]);
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

                client.subscribe('/topic/canvas/clear', () => {
                    if (onDrawRef.current) onDrawRef.current({ type: 'clear' });
                });

                client.subscribe('/topic/undo', (msg) => {
                    const { actionId } = JSON.parse(msg.body);
                    if (onDrawRef.current) onDrawRef.current({ type: 'undo', actionId });
                });

                // 2. Subscribe to User Queue (Private)
                client.subscribe('/user/queue/users', (msg) => updateUsers(JSON.parse(msg.body)));
                client.subscribe('/user/queue/scoreboard', (msg) => setRanking(JSON.parse(msg.body)));
                client.subscribe('/user/queue/word', (msg) => setMySecretWord(msg.body || null));
                client.subscribe('/user/queue/wordlen', (msg) => setWordLen(parseInt(msg.body, 10)));

                client.subscribe('/user/queue/errors', (msg) => {
                    setChatMessages(prev => [...prev, { from: 'SYSTEM', text: msg.body, system: true }]);
                });

                client.subscribe('/user/queue/draw', (msg) => {
                    // Snapshot replay
                    if (onDrawRef.current) onDrawRef.current(JSON.parse(msg.body));
                });

                client.subscribe('/user/queue/canvas/clear', () => {
                    if (onDrawRef.current) onDrawRef.current({ type: 'clear' });
                });

                client.subscribe('/user/queue/force-logout', (msg) => {
                    // Security check (simplified)
                    window.location.href = '/login?logout';
                });

                // 3. Request Initial State
                client.publish({ destination: '/app/state.sync', body: '{}' });
            },
            onStompError: (frame) => {
                console.error('Broker error:', frame.headers['message']);
            }
        });

        client.activate();
        clientRef.current = client;

        return () => {
            if (clientRef.current) {
                clientRef.current.deactivate();
            }
        };
    }, [user?.name, updateUsers]);

    // Actions
    const sendChat = (text) => {
        if (!clientRef.current || !connected || !user?.name) return;
        clientRef.current.publish({
            destination: '/app/chat.send',
            body: JSON.stringify({ from: user.name, text })
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
