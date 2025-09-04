"""
日志与计时工具

提供同步/异步上下文计时器，以及函数装饰器 `timer`：
- 统一输出开始/结束日志，自动携带 `RequestIdCtx.request_id`
- 捕获异常并打印堆栈
- 用于衡量关键函数与中间件的耗时
"""
import asyncio
import functools
import time
import traceback
from loguru import logger

from genie_tool.model.context import RequestIdCtx


class Timer(object):
    """同步计时上下文管理器"""
    def __init__(self, key: str):
        self.key = key

    def __enter__(self):
        self.start_time = time.time()
        logger.info(f"{RequestIdCtx.request_id} {self.key} start...")
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type is not None:
            logger.error(f"{RequestIdCtx.request_id} {self.key} error={exc_tb}")
        else:
            logger.info(f"{RequestIdCtx.request_id} {self.key} cost=[{int((time.time() - self.start_time) * 1000)} ms]")


class AsyncTimer(object):
    """异步计时上下文管理器（async with）"""
    def __init__(self, key: str):
        self.key = key

    async def __aenter__(self):
        self.start_time = time.time()
        logger.info(f"{RequestIdCtx.request_id} {self.key} start...")
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if exc_type is not None:
            logger.error(f"{RequestIdCtx.request_id} {self.key} error={traceback.format_exc()}")
        else:
            logger.info(f"{RequestIdCtx.request_id} {self.key} cost=[{int((time.time() - self.start_time) * 1000)} ms]")


def timer(key: str = ""):
    """
    函数耗时装饰器

    - 异步函数：使用 AsyncTimer
    - 同步函数：使用 Timer
    """
    def decorator(func):
        if asyncio.iscoroutinefunction(func):
            @functools.wraps(func)
            async def wrapper(*args, **kwargs):
                async with AsyncTimer(f"{key} {func.__name__}"):
                    result = await func(*args, **kwargs)
                return result
            return wrapper
        else:
            @functools.wraps(func)
            def wrapper(*args, **kwargs):
                with Timer(f"{key} {func.__name__}"):
                    result = func(*args, **kwargs)
                return result
            return wrapper
    return decorator


if __name__ == "__main__":
    pass
