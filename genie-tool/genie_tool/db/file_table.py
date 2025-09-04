"""
文件信息数据表模型

使用 SQLModel 定义文件记录结构，包含元信息与创建时间。
"""
from datetime import datetime
from typing import Optional

from sqlalchemy import DateTime, text
from sqlmodel import SQLModel, Field


class FileInfo(SQLModel, table=True):
    id: int | None = Field(default=None, primary_key=True)
    file_id: str = Field(unique=True, nullable=True)
    filename: str = Field()
    file_path: str = Field()
    description: Optional[str]
    file_size: Optional[int]
    status: int = Field(default=0)
    request_id: Optional[str] = Field(default=None)
    create_time: Optional[datetime] = Field(
        sa_type=DateTime, default=None, nullable=False,  sa_column_kwargs={"server_default": text("CURRENT_TIMESTAMP")}
    )