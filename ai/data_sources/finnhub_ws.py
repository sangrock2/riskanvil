"""
Finnhub WebSocket 실시간 시세 클라이언트
"""
import asyncio
import json
import logging
from typing import Callable, Optional

import websockets

from config import FINNHUB_KEY

logger = logging.getLogger("app")


class FinnhubWebSocket:
    """Finnhub WebSocket 관리자"""

    def __init__(self, api_key: str = None):
        self.api_key = api_key or FINNHUB_KEY
        self.url = f"wss://ws.finnhub.io?token={self.api_key}"
        self.subscriptions: set[str] = set()
        self.latest_prices: dict[str, dict] = {}  # ticker -> {price, volume, timestamp}
        self.callbacks: list[Callable] = []
        self._ws: Optional[websockets.WebSocketClientProtocol] = None
        self._running = False
        self._reconnect_delay = 1

    async def connect(self):
        """Finnhub WebSocket 연결"""
        if not self.api_key:
            logger.warning("FINNHUB_API_KEY not set, skipping WebSocket connection")
            return

        self._running = True
        while self._running:
            try:
                async with websockets.connect(self.url) as ws:
                    self._ws = ws
                    self._reconnect_delay = 1
                    logger.info("Finnhub WebSocket connected")

                    # 기존 구독 복원
                    for ticker in self.subscriptions:
                        await self._send_subscribe(ws, ticker)

                    # 메시지 수신 루프
                    async for message in ws:
                        await self._handle_message(message)

            except websockets.exceptions.ConnectionClosed:
                logger.warning("Finnhub WebSocket disconnected, reconnecting...")
            except Exception as e:
                logger.error(f"Finnhub WebSocket error: {e}")

            if self._running:
                await asyncio.sleep(self._reconnect_delay)
                self._reconnect_delay = min(self._reconnect_delay * 2, 30)

    async def disconnect(self):
        """연결 종료"""
        self._running = False
        if self._ws:
            await self._ws.close()
            self._ws = None

    async def subscribe(self, ticker: str):
        """종목 구독"""
        self.subscriptions.add(ticker)
        if self._ws:
            await self._send_subscribe(self._ws, ticker)

    async def unsubscribe(self, ticker: str):
        """종목 구독 해제"""
        self.subscriptions.discard(ticker)
        if self._ws:
            try:
                await self._ws.send(json.dumps({"type": "unsubscribe", "symbol": ticker}))
            except Exception:
                pass

    def on_price_update(self, callback: Callable):
        """가격 업데이트 콜백 등록"""
        self.callbacks.append(callback)

    def get_latest_price(self, ticker: str) -> Optional[dict]:
        """최신 가격 조회"""
        return self.latest_prices.get(ticker)

    async def _send_subscribe(self, ws, ticker: str):
        try:
            await ws.send(json.dumps({"type": "subscribe", "symbol": ticker}))
        except Exception as e:
            logger.error(f"Failed to subscribe {ticker}: {e}")

    async def _handle_message(self, message: str):
        try:
            data = json.loads(message)
            if data.get("type") == "trade":
                trades = data.get("data", [])
                for trade in trades:
                    ticker = trade.get("s")
                    price = trade.get("p")
                    volume = trade.get("v")
                    timestamp = trade.get("t")

                    if ticker and price:
                        update = {
                            "ticker": ticker,
                            "price": price,
                            "volume": volume,
                            "timestamp": timestamp,
                        }
                        self.latest_prices[ticker] = update

                        # 콜백 호출
                        for cb in self.callbacks:
                            try:
                                if asyncio.iscoroutinefunction(cb):
                                    await cb(update)
                                else:
                                    cb(update)
                            except Exception as e:
                                logger.error(f"Callback error: {e}")

        except json.JSONDecodeError:
            pass
        except Exception as e:
            logger.error(f"Handle message error: {e}")


# 싱글톤 인스턴스
finnhub_ws = FinnhubWebSocket()
