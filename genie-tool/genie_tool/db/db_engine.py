"""
数据库引擎与会话管理

提供：
- 同步 engine（用于建表）
- 异步 engine + session 工厂（业务查询）
- FastAPI 依赖的异步 session 生成器
"""
import os
from typing import Callable, AsyncGenerator

from loguru import logger
from sqlalchemy import AsyncAdaptedQueuePool, create_engine
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
from sqlalchemy.orm import sessionmaker
from sqlmodel import SQLModel


SQLITE_DB_PATH = os.environ.get("SQLITE_DB_PATH", "autobots.db")

engine = create_engine(f"sqlite:///{SQLITE_DB_PATH}", echo=True)

async_engine = create_async_engine(
    f"sqlite+aiosqlite:///{SQLITE_DB_PATH}",
    poolclass=AsyncAdaptedQueuePool,
    pool_size=10,
    pool_recycle=3600,
    echo=False,
)

async_session_local: Callable[..., AsyncSession] = sessionmaker(bind=async_engine, class_=AsyncSession)


async def get_async_session() -> AsyncGenerator[AsyncSession, None]:
    """异步 session 生成器（FastAPI Depends 使用）"""
    async with async_session_local() as session:
        yield session


def init_db():
    """初始化数据库并建表"""
    from genie_tool.db.file_table import FileInfo
    SQLModel.metadata.create_all(engine)
    logger.info(f"DB init done")


if __name__ == "__main__":
    init_db()
