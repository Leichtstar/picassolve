import React, { useState, useEffect } from 'react';
import { backendFetch } from '../lib/backend';

export default function RankingBoard({ liveRanking }) {
    const [period, setPeriod] = useState('LIVE');
    const [apiRanking, setApiRanking] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (period === 'LIVE') return;

        setLoading(true);
        backendFetch(`/api/rankings?period=${period}`, { credentials: 'include' })
            .then(res => {
                if (!res.ok) throw new Error('Failed');
                return res.json();
            })
            .then(data => setApiRanking(data))
            .catch(() => setApiRanking([]))
            .finally(() => setLoading(false));
    }, [period]);

    const list = period === 'LIVE' ? liveRanking : apiRanking;

    return (
        <div className="panel ranking-panel">
            <div className="panel-head">
                <h3>🏆 랭킹</h3>
                <select value={period} onChange={(e) => setPeriod(e.target.value)}>
                    <option value="LIVE">실시간</option>
                    <option value="DAILY">일간</option>
                    <option value="WEEKLY">주간</option>
                    <option value="MONTHLY">월간</option>
                </select>
            </div>
            <ol className="ranking">
                {loading && <li className="muted">불러오는 중...</li>}
                {!loading && list.length === 0 && <li className="muted">데이터 없음</li>}
                {!loading && list.map((item, i) => (
                    <li key={i}>
                        {item.name}/{item.team} : {item.score}점
                    </li>
                ))}
            </ol>
        </div>
    );
}
