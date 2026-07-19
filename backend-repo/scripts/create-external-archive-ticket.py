#!/usr/bin/env python3
"""Create a signed MRR external archive ticket using only Python stdlib."""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from typing import Any

TICKET_PATH = "/api/v1/integration/archive/tickets"


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
    canonical = f"POST\n{TICKET_PATH}\n{timestamp}\n{nonce}\n{body_hash}".encode("utf-8")
    return hmac.new(secret.encode("utf-8"), canonical, hashlib.sha256).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description="申请 MRR 外部影像档案袋一次性访问票据")
    parser.add_argument("--base-url", required=True, help="MRR 地址，例如 https://mrr.example.internal")
    parser.add_argument("--client-id", required=True, help="外部系统 client-id")
    parser.add_argument("--secret", required=True, help="外部系统 HMAC 密钥")
    parser.add_argument("--external-user-id", required=True, help="外部系统当前登录用户 ID")
    parser.add_argument("--id-card", help="身份证号")
    parser.add_argument("--bah", action="append", default=[], help="病案号，可重复传入")
    parser.add_argument("--sjh", action="append", default=[], help="上架号，可重复传入")
    parser.add_argument(
        "--archive",
        action="append",
        type=parse_pair,
        default=[],
        metavar="BAH:SJH",
        help="精确病案号与上架号配对，可重复传入",
    )
    parser.add_argument("--allow-download", action="store_true", help="允许服务器批量导出 ZIP")
    args = parser.parse_args()

    try:
        payload = build_payload(args)
    except ValueError as exc:
        parser.error(str(exc))

    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    timestamp = str(int(time.time()))
    nonce = str(uuid.uuid4())
    signature = sign(args.secret, timestamp, nonce, body)
    url = args.base_url.rstrip("/") + TICKET_PATH
    request = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "X-MRR-Client-Id": args.client_id,
            "X-MRR-Timestamp": timestamp,
            "X-MRR-Nonce": nonce,
            "X-MRR-Signature": signature,
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        print(f"HTTP {exc.code}: {detail}", file=sys.stderr)
        return 1
    except urllib.error.URLError as exc:
        print(f"请求失败: {exc}", file=sys.stderr)
        return 1

    print(json.dumps(result, ensure_ascii=False, indent=2))
    launch_url = (result.get("data") or {}).get("launchUrl")
    if launch_url:
        print(f"\n打开地址：{launch_url}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
