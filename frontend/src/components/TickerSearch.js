import React, { useEffect, useState } from "react";
import { apiFetch } from "../api/http";

export default function TickerSearch({ market, onPick }) {
    const [q, setQ] = useState("");
    const [items, setItems] = useState([]);
    const [err, setErr] = useState("");

    useEffect(() => {
        if (!q.trim()) { setItems([]); return; }

        const t = setTimeout(async () => {
            try {
                setErr("");
                const list = await apiFetch(`/api/market/search?keywords=${encodeURIComponent(q)}&market=${market}`);
                setItems(Array.isArray(list) ? list : []);
            } catch (e) {
                setErr(e.message);
                setItems([]);
            }
        }, 250);

        return () => clearTimeout(t);
    }, [q, market]);

    return (
        <div style={{ position: "relative", minWidth: 260 }}>
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="종목 검색 (예: AAPL, TSLA...)" style={{ width: "100%", padding: 8 }}/>

            {err ? <div style={{ color: "crimson", fontSize: 12 }}>{err}</div> : null}

            {items.length > 0 && (
                <div style={{position: "absolute", zIndex: 10, background: "white", border: "1px solid #ddd", width: "100%", maxHeight: 240, overflow: "auto"}}>
                    {items.map((it) => (
                        <div key={it.symbol} onClick={() => { onPick(it.symbol); setItems([]); setQ(it.symbol); }} style={{ padding: 8, cursor: "pointer", borderBottom: "1px solid #f1f1f1" }}>
                            <b>{it.symbol}</b> — {it.name} <span style={{ color: "#666" }}>({it.region})</span>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
