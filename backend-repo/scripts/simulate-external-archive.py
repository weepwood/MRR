#!/usr/bin/env python3
"""Simulate an external system reading an MRR image archive.

The tool exercises the complete server-to-server/browser hand-off:
ticket request, one-time ticket exchange, context lookup, image lookup,
optional image redirect/download, and logout. It uses only Python stdlib.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
from pathlib import Path
from typing import Any

TICKET_PATH = "/api/v1/integration/archive/tickets"
SESSION_PATH = "/api/v1/external/archive/session"
CONTEXT_PATH = "/api/v1/external/archive/context"
IMAGES_PATH = "/api/v1/external/archive/images"
IMAGE_PATH = "/api/v1/external/archive/image"
DOWNLOAD_PATH = "/api/v1/external/archive/download"
LOGOUT_PATH = "/api/v1/external/archive/logout"


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def parse_pair(value: str) -> dict[str, str]:
    parts = value.split(":", 1)
    if len(parts) != 2 or not all(part.strip() for part in parts):
        raise argparse.ArgumentTypeError("精确病案必须使用 BAH:SJH 格式")
    return {"bah": parts[0].strip(), "sjh": parts[1].strip()}


def build_payload(args: argparse.Namespace) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "externalUserId": args.external_user_id,
        "allowDownload": args.allow_download,
    }
    if args.id_card:
        payload["idCard"] = args.id_card
    if len(args.bah) == 1:
        payload["bah"] = args.bah[0]
    elif args.bah:
        payload["bahs"] = args.bah
    if len(args.sjh) == 1:
        payload["sjh"] = args.sjh[0]
    elif args.sjh:
        payload["sjhs"] = args.sjh
    if args.archive:
        payload["archives"] = args.archive
    if not any(key in payload for key in ("idCard", "bah", "sjh", "bahs", "sjhs", "archives")):
        raise ValueError("至少提供一种访问条件：身份证、病案号、上架号或精确病案配对")
    return payload


def sign(secret: str, timestamp: str, nonce: str, body: bytes) -> str:
    body_hash = hashlib.sha256(body).hexdigest()
    canonical = f"POST\n{TICKET_PATH}\n{timestamp}\n{nonce}\n{body_hash}"
    return hmac.new(secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).hexdigest()


def api_url(base_url: str, path: str) -> str:
    return base_url.rstrip("/") + path


def read_response(response: Any) -> tuple[int, bytes, dict[str, str]]:
    return response.status, response.read(), dict(response.headers.items())


def request_json(
    opener: urllib.request.OpenerDirector,
    url: str,
    *,
    method: str = "GET",
    body: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout: int,
) -> tuple[int, dict[str, Any]]:
    request = urllib.request.Request(url, data=body, method=method, headers=headers or {})
    try:
        with opener.open(request, timeout=timeout) as response:
            status, raw, _ = read_response(response)
    except urllib.error.HTTPError as exc:
        status, raw, _ = read_response(exc)
    except urllib.error.URLError as exc:
        raise RuntimeError(f"请求失败 {url}: {exc.reason}") from exc
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"接口返回不是 JSON（HTTP {status}）: {raw[:200]!r}") from exc
    if not isinstance(data, dict):
        raise RuntimeError(f"接口返回格式异常（HTTP {status}）")
    return status, data


def require_success(step: str, status: int, data: dict[str, Any]) -> dict[str, Any]:
    if status < 200 or status >= 300 or data.get("code") not in (None, 200):
        raise RuntimeError(f"{step}失败: HTTP {status}, response={json.dumps(data, ensure_ascii=False)}")
    return data.get("data") or {}


def request_image_redirect(
    opener: urllib.request.OpenerDirector, url: str, timeout: int
) -> tuple[int, str | None]:
    request = urllib.request.Request(url, method="GET")
    try:
        with opener.open(request, timeout=timeout) as response:
            return response.status, response.headers.get("Location")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.headers.get("Location")


def download_archive(
    opener: urllib.request.OpenerDirector, url: str, output: Path, timeout: int
) -> int:
    request = urllib.request.Request(url, method="GET")
    try:
        with opener.open(request, timeout=timeout) as response:
            content = response.read()
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_bytes(content)
            try:
                with zipfile.ZipFile(output) as archive:
                    if not archive.infolist():
                        raise RuntimeError(f"下载的 ZIP 不包含任何影像文件: {output}")
            except zipfile.BadZipFile as exc:
                raise RuntimeError(f"下载结果不是有效 ZIP: {output}") from exc
            return response.status
    except urllib.error.HTTPError as exc:
        detail = exc.read(200).decode("utf-8", errors="replace")
        raise RuntimeError(f"下载失败: HTTP {exc.code}, {detail}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"下载失败: {exc.reason}") from exc
    except OSError as exc:
        raise RuntimeError(f"无法写入下载文件 {output}: {exc}") from exc


def print_step(name: str, status: int, detail: str) -> None:
    print(f"[{status}] {name}: {detail}")


def main() -> int:
    parser = argparse.ArgumentParser(description="模拟外部系统完整访问 MRR 影像档案袋")
    parser.add_argument("--base-url", required=True, help="MRR 地址，例如 http://localhost:18045")
    parser.add_argument("--client-id", required=True, help="外部系统 client-id")
    parser.add_argument("--secret", required=True, help="外部系统 HMAC 密钥")
    parser.add_argument("--external-user-id", required=True, help="外部系统当前用户 ID")
    parser.add_argument("--id-card", help="身份证号")
    parser.add_argument("--bah", action="append", default=[], help="病案号，可重复")
    parser.add_argument("--sjh", action="append", default=[], help="上架号，可重复")
    parser.add_argument("--archive", action="append", type=parse_pair, default=[], metavar="BAH:SJH", help="精确病案配对，可重复")
    parser.add_argument("--allow-download", action="store_true", help="申请批量下载权限")
    parser.add_argument("--download", metavar="ZIP_PATH", help="下载首个档案 ZIP 到指定文件")
    parser.add_argument("--image-id", type=int, help="指定要测试重定向的影像 ID；默认使用首个影像")
    parser.add_argument("--timeout", type=int, default=30, help="每个请求超时秒数，默认 30")
    args = parser.parse_args()

    if args.download and not args.allow_download:
        parser.error("使用 --download 时必须同时指定 --allow-download")

    try:
        payload = build_payload(args)
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        timestamp = str(int(time.time()))
        nonce = str(uuid.uuid4())
        signature = sign(args.secret, timestamp, nonce, body)
        ticket_headers = {
            "Content-Type": "application/json; charset=utf-8",
            "X-MRR-Client-Id": args.client_id,
            "X-MRR-Timestamp": timestamp,
            "X-MRR-Nonce": nonce,
            "X-MRR-Signature": signature,
        }

        cookie_jar = urllib.request.HTTPCookieProcessor()
        opener = urllib.request.build_opener(cookie_jar)
        no_redirect_opener = urllib.request.build_opener(cookie_jar, NoRedirectHandler())
        ticket_status, ticket_json = request_json(
            opener, api_url(args.base_url, TICKET_PATH), method="POST", body=body,
            headers=ticket_headers, timeout=args.timeout,
        )
        ticket_data = require_success("申请票据", ticket_status, ticket_json)
        ticket = ticket_data.get("ticket")
        if not ticket:
            raise RuntimeError(f"申请票据响应缺少 ticket: {ticket_data}")
        print_step("申请一次性票据", ticket_status, f"archiveCount={ticket_data.get('archiveCount')}, expiresIn={ticket_data.get('expiresIn')}s")

        session_status, session_json = request_json(
            opener, api_url(args.base_url, SESSION_PATH), method="POST",
            body=json.dumps({"ticket": ticket}, separators=(",", ":")).encode("utf-8"),
            headers={"Content-Type": "application/json"}, timeout=args.timeout,
        )
        session_data = require_success("兑换会话", session_status, session_json)
        print_step("兑换短期会话", session_status, f"clientId={session_data.get('clientId')}, allowDownload={session_data.get('allowDownload')}")

        context_status, context_json = request_json(opener, api_url(args.base_url, CONTEXT_PATH), timeout=args.timeout)
        context_data = require_success("读取会话上下文", context_status, context_json)
        cases = context_data.get("cases") or []
        print_step("读取会话上下文", context_status, f"authorizedArchives={len(cases)}")
        if not cases:
            raise RuntimeError("会话未返回任何授权档案")

        selected = cases[0]
        bah, sjh = selected.get("bah", ""), selected.get("sjh", "")
        query = urllib.parse.urlencode({key: value for key, value in (("bah", bah), ("sjh", sjh)) if value})
        images_status, images_json = request_json(opener, api_url(args.base_url, f"{IMAGES_PATH}?{query}"), timeout=args.timeout)
        images_data = require_success("读取档案影像", images_status, images_json)
        print_step("读取档案影像", images_status, f"bah={bah}, sjh={sjh}, images={len(images_data) if isinstance(images_data, list) else 0}")

        image_id = args.image_id
        if image_id is None and isinstance(images_data, list) and images_data:
            image_id = images_data[0].get("id")
        if image_id is not None:
            redirect_status, location = request_image_redirect(no_redirect_opener, api_url(args.base_url, f"{IMAGE_PATH}/{image_id}"), args.timeout)
            if redirect_status not in (301, 302, 303, 307, 308) or not location:
                raise RuntimeError(f"读取影像重定向失败: HTTP {redirect_status}, location={location}")
            print_step("读取影像地址", redirect_status, f"imageId={image_id}, location={location}")

        if args.download:
            download_query = urllib.parse.urlencode({key: value for key, value in (("bah", bah), ("sjh", sjh)) if value})
            download_status = download_archive(opener, api_url(args.base_url, f"{DOWNLOAD_PATH}?{download_query}"), Path(args.download), args.timeout)
            print_step("下载档案 ZIP", download_status, f"file={args.download}")

        logout_status, logout_json = request_json(opener, api_url(args.base_url, LOGOUT_PATH), method="POST", timeout=args.timeout)
        require_success("退出会话", logout_status, logout_json)
        print_step("退出外部会话", logout_status, "session revoked")
        print("模拟访问完成")
        return 0
    except (RuntimeError, ValueError) as exc:
        print(f"模拟访问失败: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
