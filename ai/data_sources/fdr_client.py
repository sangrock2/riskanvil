"""
FinanceDataReader 클라이언트 - 한국 시장 데이터
"""
import asyncio
import logging
from typing import Optional

import FinanceDataReader as fdr
import pandas as pd

logger = logging.getLogger("app")


def _run_sync(func, *args, **kwargs):
    return asyncio.to_thread(func, *args, **kwargs)


async def load_kr_prices(ticker: str, start: Optional[str] = None, end: Optional[str] = None) -> pd.DataFrame:
    """한국 시장 가격 데이터 로드"""
    def _fetch():
        df = fdr.DataReader(ticker, start, end)
        if df is None or df.empty:
            return None
        df = df.reset_index()
        df.columns = [c.lower() for c in df.columns]
        df.rename(columns={"date": "dt"}, inplace=True)
        df["dt"] = pd.to_datetime(df["dt"])
        df = df.sort_values("dt")
        return df

    return await _run_sync(_fetch)
