import React, { useMemo } from 'react';

export default function UserList({ users }) {

    const sortedUsers = useMemo(() => {
        // Stable sort: Drawer(0) < Admin(1) < Participant(2)
        const getPriority = (role) => {
            if (role === 'DRAWER') return 0;
            if (role === 'ADMIN') return 1;
            return 2;
        };

        return [...users].sort((a, b) => { // users is [{name, role}, ...]
            const pa = getPriority(a.role);
            const pb = getPriority(b.role);
            if (pa !== pb) return pa - pb;
            return 0; // Keep original order if priority same
        });
    }, [users]);

    return (
        <div className="panel users-panel">
            <h3>👀 참여자</h3>
            <ul className="users">
                {sortedUsers.map((u, i) => (
                    <li key={u.name + i}>
                        {u.name}
                        {u.role === 'ADMIN' && ' 🛠️'}
                        {u.role === 'DRAWER' && ' 🎨'}
                    </li>
                ))}
            </ul>
        </div>
    );
}
