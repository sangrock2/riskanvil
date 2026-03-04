# request_id.py
import logging
from contextvars import ContextVar

_request_id_ctx: ContextVar[str] = ContextVar("request_id", default="-")

def set_request_id(rid: str):
    rid = (rid or "-").strip()
    return _request_id_ctx.set(rid if rid else "-")

def reset_request_id(token):
    _request_id_ctx.reset(token)

def get_request_id() -> str:
    return _request_id_ctx.get()

def request_id_header() -> dict:
    """아웃바운드 호출(httpx 등)에 붙일 헤더"""
    rid = get_request_id()
    return {"X-Request-Id": rid} if rid and rid != "-" else {}

class RequestIdLogFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = get_request_id()
        return True

def setup_logging():
    """
    - 기존 로깅 설정이 있어도 안전하게 rid 필터만 주입
    - uvicorn 핸들러가 나중에 생겨도 로거 필터로 커버
    """
    flt = RequestIdLogFilter()

    # root logger
    root = logging.getLogger()
    root.addFilter(flt)

    # 핸들러가 없을 때만 기본 핸들러를 추가(기존 설정 존중)
    if not root.handlers:
        fmt = "%(asctime)s %(levelname)s [rid=%(request_id)s] %(name)s - %(message)s"
        handler = logging.StreamHandler()
        handler.setFormatter(logging.Formatter(fmt))
        handler.addFilter(flt)
        root.addHandler(handler)
        root.setLevel(logging.INFO)
    else:
        # 이미 핸들러가 있으면 거기에 필터만 추가
        for h in root.handlers:
            h.addFilter(flt)

    # uvicorn loggers에도 "로거 필터"로 주입 (핸들러가 나중에 생겨도 적용됨)
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        lg = logging.getLogger(name)
        lg.addFilter(flt)
        for h in lg.handlers:
            h.addFilter(flt)
