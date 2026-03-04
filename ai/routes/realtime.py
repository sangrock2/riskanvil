"""
실시간 WebSocket 라우트 - Finnhub 시세 스트리밍
"""
import asyncio
import json
import logging

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from data_sources.finnhub_ws import finnhub_ws

logger = logging.getLogger("app")

router = APIRouter()

# 연결된 클라이언트 관리
_clients: set[WebSocket] = set()
_client_subscriptions: dict[WebSocket, set[str]] = {}


async def _broadcast_price(update: dict):
    """가격 업데이트를 구독 중인 클라이언트에 브로드캐스트"""
    ticker = update.get("ticker")
    if not ticker:
        return

    dead_clients = set()
    for ws in _clients:
        subs = _client_subscriptions.get(ws, set())
        if ticker in subs:
            try:
                await ws.send_json(update)
            except Exception:
                dead_clients.add(ws)

    # 끊긴 클라이언트 정리
    for ws in dead_clients:
        _clients.discard(ws)
        _client_subscriptions.pop(ws, None)


# Finnhub 콜백 등록
finnhub_ws.on_price_update(_broadcast_price)


@router.websocket("/ws/quotes")
async def websocket_quotes(ws: WebSocket):
    """실시간 시세 WebSocket 엔드포인트"""
    await ws.accept()
    _clients.add(ws)
    _client_subscriptions[ws] = set()

    logger.info("WebSocket client connected")

    try:
        while True:
            data = await ws.receive_text()
            try:
                msg = json.loads(data)
                action = msg.get("action")
                ticker = msg.get("ticker", "").upper()

                if not ticker:
                    continue

                if action == "subscribe":
                    _client_subscriptions[ws].add(ticker)
                    await finnhub_ws.subscribe(ticker)
                    # 최신 가격이 있으면 바로 전송
                    latest = finnhub_ws.get_latest_price(ticker)
                    if latest:
                        await ws.send_json(latest)
                    logger.info(f"Client subscribed to {ticker}")

                elif action == "unsubscribe":
                    _client_subscriptions[ws].discard(ticker)
                    # 다른 클라이언트가 아직 구독 중인지 확인
                    still_needed = any(
                        ticker in subs
                        for client, subs in _client_subscriptions.items()
                        if client != ws
                    )
                    if not still_needed:
                        await finnhub_ws.unsubscribe(ticker)
                    logger.info(f"Client unsubscribed from {ticker}")

            except json.JSONDecodeError as e:
                logger.warning(f"Invalid JSON received from WebSocket client: {e}")
                # Continue processing other messages

    except WebSocketDisconnect:
        logger.info("WebSocket client disconnected")
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
    finally:
        _clients.discard(ws)
        subs = _client_subscriptions.pop(ws, set())
        # 정리: 더 이상 구독자 없는 티커 해제
        for ticker in subs:
            still_needed = any(
                ticker in s for s in _client_subscriptions.values()
            )
            if not still_needed:
                await finnhub_ws.unsubscribe(ticker)
