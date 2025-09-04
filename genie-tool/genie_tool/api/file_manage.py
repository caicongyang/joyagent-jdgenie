"""
文件服务 API（FastAPI 路由）

提供文件的上传、下载、预览与查询能力：
- /get_file：根据 file_id 获取文件链接与基础信息
- /upload_file：上传内存内容（JSON）
- /upload_file_data：上传二进制文件（multipart）
- /get_file_list：按条件批量查询文件信息
- /download/{file_id}/{file_name}：下载文件
- /preview/{file_id}/{file_name}：预览文件（尝试根据后缀推断 content-type）
"""

import mimetypes
import os
from urllib.parse import quote, unquote

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import JSONResponse, Response, FileResponse

from genie_tool.model.protocal import FileRequest, FileListRequest, FileUploadRequest, get_file_id
from genie_tool.util.middleware_util import RequestHandlerRoute
from genie_tool.db.file_table_op import FileInfoOp, get_file_preview_url, get_file_download_url


router = APIRouter(route_class=RequestHandlerRoute)


@router.post("/get_file")
async def get_file(
        body: FileRequest
):
    """获取单个文件的链接信息（预览/下载）"""
    file_info = await FileInfoOp.get_by_file_id(file_id=body.file_id)
    if file_info:
        preview_url = get_file_preview_url(file_id=file_info.request_id, file_name=file_info.filename)
        download_url = get_file_download_url(file_id=file_info.request_id, file_name=file_info.filename)
        return JSONResponse(
            content={"ossUrl": download_url, "downloadUrl": download_url, "domainUrl": preview_url, "requestId": body.request_id,
                     "fileName": body.file_name})
    else:
        raise Exception("file not found")


@router.post("/upload_file")
async def upload_file(
        body: FileUploadRequest
):
    """上传文本内容到文件系统（由后端保存为文件）"""
    file_info = await FileInfoOp.add_by_content(
        filename=body.file_name, content=body.content, file_id=body.file_id, description=body.description,
        request_id=body.request_id)
    preview_url = get_file_preview_url(file_id=file_info.request_id, file_name=file_info.filename)
    download_url = get_file_download_url(file_id=file_info.request_id, file_name=file_info.filename)
    return JSONResponse(content={"ossUrl": download_url, "downloadUrl": download_url, "domainUrl": preview_url, "fileSize": file_info.file_size})

@router.post("/upload_file_data")
async def upload_file_data(file: UploadFile = File(...), request_id: str = Form(alias="requestId")):
    """上传二进制文件（multipart/form-data）"""
    file.filename = unquote(file.filename)
    file_id = get_file_id(request_id, file.filename)
    file_info = await FileInfoOp.add_by_file(file=file, file_id=file_id, request_id=request_id)
    preview_url = get_file_preview_url(file_id=file_info.request_id, file_name=file_info.filename)
    download_url = get_file_download_url(file_id=file_info.request_id, file_name=file_info.filename)
    return JSONResponse(content={"downloadUrl": download_url, "domainUrl": preview_url, "fileSize": file_info.file_size})


@router.post("/get_file_list")
async def get_file_list(body: FileListRequest):
    """按条件查询文件列表并汇总大小"""
    if not body.filters:
        file_infos = await FileInfoOp.get_by_request_id(body.request_id)
    else:
        file_infos = await FileInfoOp.get_by_file_ids(file_ids=[f.file_id for f in body.filters])
    if not file_infos:
         return JSONResponse(content={"results": [], "totalSize": 0})
    total_size = sum([f.file_size for f in file_infos])
    results = []
    for file_info in file_infos:
        preview_url = get_file_preview_url(file_id=file_info.request_id, file_name=file_info.filename)
        download_url = get_file_download_url(file_id=file_info.request_id, file_name=file_info.filename)
        results.append({
            "ossUrl": download_url,
            "downloadUrl": download_url, "domainUrl": preview_url,
            "requestId": file_info.request_id, "fileName": file_info.filename
        })
    return JSONResponse(content={"results": results, "totalSize": total_size})


@router.get("/download/{file_id}/{file_name}")
async def download_file(file_id: str, file_name: str):
    # TODO 目前 file_id 实际上是 request_id，后续统一修改
    """下载文件：若不存在则返回 404"""
    file_id = get_file_id(file_id, file_name)
    file_info = await FileInfoOp.get_by_file_id(file_id=file_id)
    if not file_info or not os.path.exists(file_info.file_path):
        return Response(content="File not found", status_code=404)
    return FileResponse(file_info.file_path, filename=os.path.basename(file_name))


@router.get("/preview/{file_id}/{file_name}")
async def preview_file(file_id: str, file_name: str):
    # TODO 目前 file_id 实际上是 request_id，后续统一修改
    """预览文件：尽量推断合适的 Content-Type；无法推断时降级为下载"""
    file_id = get_file_id(file_id, file_name)
    file_info = await FileInfoOp.get_by_file_id(file_id=file_id)
    if not file_info or not os.path.exists(file_info.file_path):
        return Response(content="File not found", status_code=404)

    disposition = "inline"
    if file_name.endswith(".md"):
        content_type = "text/markdown"
    else:
        content_type, _ = mimetypes.guess_type(file_name)
    if not content_type:
        content_type = "application/octet-stream"
        disposition = "attachment"

    encoded_file_name = quote(file_name)

    return FileResponse(
        file_info.file_path,
        filename=os.path.basename(file_name),
        media_type=content_type,
        headers={
            "Content-Disposition": f"{disposition}; filename=\"{encoded_file_name}\"; filename*=UTF-8''{encoded_file_name}",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
            "Access-Control-Allow-Headers": "Content-Type, Authorization",
        }
    )

