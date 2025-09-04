"""
中间件与请求路由工具

包含：
- UnknownException：捕获未处理异常并返回 500
- RequestHandlerRoute：打印 POST 非 multipart 请求的 body（便于排障）
- HTTPProcessTimeMiddleware：为每个请求生成 request_id 并记录处理耗时
"""
import time
import traceback
import uuid
from typing import Callable

from fastapi.routing import APIRoute
from loguru import logger
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

from genie_tool.model.context import RequestIdCtx
from genie_tool.util.log_util import AsyncTimer


class UnknownException(BaseHTTPMiddleware):
    """全局异常兜底中间件：避免未捕获异常导致 500 栈信息外泄"""

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        try:
            return await call_next(request)
        except Exception as e:
            logger.error(f"{RequestIdCtx.request_id} {request.method} {request.url.path} error={traceback.format_exc()}")
            return Response(content=f"Unexpected error: {e}", status_code=500)


class RequestHandlerRoute(APIRoute):
    """
    自定义路由：
    - 对 POST 且非 multipart/form-data 的请求，打印 body 内容到日志
    - 其他请求透传
    """

    def get_route_handler(self) -> Callable:
        original_route_handler = super().get_route_handler()

        async def custom_route_handler(request: Request) -> Response:
            try:
                content_type = request.headers.get('content-type', '')
                if request.method == "POST" and not content_type.startswith('multipart/form-data'):
                    body = (await request.body()).decode("utf-8")
                    logger.info(f"{RequestIdCtx.request_id} {request.method} {request.url.path} body={body}")
            except Exception as e:
                logger.warning(f"{RequestIdCtx.request_id} {request.method} {request.url.path} failed. error={e}")

            return await original_route_handler(request)

        return custom_route_handler


class HTTPProcessTimeMiddleware(BaseHTTPMiddleware):
    """记录请求处理耗时，并在响应头中注入 `X-Process-Time`"""
    async def dispatch(self, request, call_next):
        RequestIdCtx.request_id = str(uuid.uuid4())
        async with AsyncTimer(key=f"{request.method} {request.url.path}") as t:
            response = await call_next(request)
            process_time = int((time.time() - t.start_time) * 1000)
            response.headers["X-Process-Time"] = str(process_time)
        return response
