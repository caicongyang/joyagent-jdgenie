"""
请求上下文与模型信息

- RequestIdCtx：使用 contextvars 保存每次请求的 request_id，线程/协程安全
- LLMModelInfoFactory：注册并查询不同模型的上下文长度与最大输出，用于裁剪输入
"""
import contextvars

from pydantic import BaseModel


class _RequestIdCtx(object):
    """请求上下文对象：读写当前 request_id"""
    def __init__(self):
        self._request_id = contextvars.ContextVar("request_id", default="default-request-id")

    @property
    def request_id(self):
        return self._request_id.get()

    @request_id.setter
    def request_id(self, value):
        self._request_id.set(value)


RequestIdCtx = _RequestIdCtx()


class LLMModelInfo(BaseModel):
    """模型信息：上下文长度与最大输出"""
    model: str
    context_length: int
    max_output: int


class _LLMModelInfoFactory:
    """模型信息注册/查询工厂"""

    def __init__(self):
        self._factory = {}

    def register(self, model_info: LLMModelInfo):
        self._factory[model_info.model] = model_info

    def get_context_length(self, model: str, default: int = 128000) -> int:
        if info := self._factory.get(model):
            return info.context_length
        else:
            return default

    def get_max_output(self, model: str, default: int = 32000) -> int:
        if info := self._factory.get(model):
            return info.max_output
        else:
            return default


LLMModelInfoFactory = _LLMModelInfoFactory()

LLMModelInfoFactory.register(LLMModelInfo(model="gpt-4.1", context_length=1000000, max_output=32000))
LLMModelInfoFactory.register(LLMModelInfo(model="DeepSeek-V3", context_length=64000, max_output=8000))
