#!/usr/bin/env python3
"""HIS/EMR 服务端申请 MRR 外部影像档案袋一次性票据。"""

from __future__ import annotations

import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from typing import Any

TICKET_PATH = "/api/v1/integration/archive/tickets"


class MrrIntegrationError(RuntimeError):
    def __init__(
        self,
        http_status: int,
        business_code: int,
        message: str,
        response_body: str,
    ) -> None:
        super().__init__(
            f"MRR 调用失败：HTTP {http_status}, "
            f"code={business_code}, message={message}"
        )
        self.http_status = http_status
        self.business_code = business_code
        self.response_body = response_body


@dataclass(frozen=True)
class TicketResult:
    ticket: str
    launch_url: str
    expires_in: int
    archive_count: int


class MrrArchiveTicketClient:
    def __init__(self, base_url: str, client_id: str, secret: str) -> None:
        self.base_url = self._require(base_url, "base_url").rstrip("/")
        self.client_id = self._require(client_id, "client_id")
        self.secret = self._require(secret, "secret")

    def create_ticket(self, payload: dict[str, Any], timeout: int = 30) -> TicketResult:
        external_user_id = str(payload.get("externalUserId") or "").strip()
        if not external_user_id:
            raise ValueError("externalUserId 不能为空")

        # 只序列化一次：签名和 HTTP 请求必须使用完全相同的 UTF-8 字节。
        raw_body = json.dumps(
            payload,
            ensure_ascii=False,
            separators=(",", ":"),
        ).encode("utf-8")
        timestamp = str(int(time.time()))
        nonce = str(uuid.uuid4())
        body_hash = hashlib.sha256(raw_body).hexdigest()
        canonical_text = (
            f"POST\n{TICKET_PATH}\n{timestamp}\n{nonce}\n{body_hash}"
        )
        signature = hmac.new(
            self.secret.encode("utf-8"),
            canonical_text.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()

        request = urllib.request.Request(
            self.base_url + TICKET_PATH,
            data=raw_body,
            method="POST",
            headers={
                "Content-Type": "application/json; charset=utf-8",
                "X-MRR-Client-Id": self.client_id,
                "X-MRR-Timestamp": timestamp,
                "X-MRR-Nonce": nonce,
                "X-MRR-Signature": signature,
            },
        )

        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                http_status = response.status
                response_body = response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            http_status = error.code
            response_body = error.read().decode("utf-8", errors="replace")
        except urllib.error.URLError as error:
            raise RuntimeError(f"无法连接 MRR：{error.reason}") from error

        try:
            api_response = json.loads(response_body)
        except json.JSONDecodeError as error:
            raise MrrIntegrationError(
                http_status,
                http_status,
                "MRR 返回的内容不是有效 JSON",
                response_body,
            ) from error

        business_code = int(api_response.get("code", http_status))
        if not 200 <= http_status < 300 or business_code != 200:
            raise MrrIntegrationError(
                http_status,
                business_code,
                str(api_response.get("message") or "MRR 调用失败"),
                response_body,
            )

        data = api_response.get("data") or {}
        ticket = str(data.get("ticket") or "")
        launch_url = str(data.get("launchUrl") or "")
        if not ticket or not launch_url:
            raise MrrIntegrationError(
                http_status,
                business_code,
                "MRR 响应缺少 ticket 或 launchUrl",
                response_body,
            )

        return TicketResult(
            ticket=ticket,
            launch_url=launch_url,
            expires_in=int(data.get("expiresIn") or 0),
            archive_count=int(data.get("archiveCount") or 0),
        )

    def create_launch_url(self, payload: dict[str, Any], timeout: int = 30) -> str:
        return self.create_ticket(payload, timeout).launch_url

    @staticmethod
    def _require(value: str, name: str) -> str:
        normalized = (value or "").strip()
        if not normalized:
            raise ValueError(f"{name} 不能为空")
        return normalized


def require_environment(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"缺少环境变量 {name}")
    return value


def main() -> int:
    if len(sys.argv) < 4:
        print(
            "用法: python mrr_archive_ticket_client.py "
            "<externalUserId> <bah> <sjh>",
            file=sys.stderr,
        )
        print(
            "需要环境变量: MRR_BASE_URL, MRR_CLIENT_ID, MRR_HMAC_SECRET",
            file=sys.stderr,
        )
        return 2

    client = MrrArchiveTicketClient(
        require_environment("MRR_BASE_URL"),
        require_environment("MRR_CLIENT_ID"),
        require_environment("MRR_HMAC_SECRET"),
    )
    payload = {
        "externalUserId": sys.argv[1],
        "archives": [
            {
                "bah": sys.argv[2],
                "sjh": sys.argv[3],
            }
        ],
        "allowDownload": False,
    }

    try:
        result = client.create_ticket(payload)
    except MrrIntegrationError as error:
        print(str(error), file=sys.stderr)
        print(error.response_body, file=sys.stderr)
        return 1

    print(f"archiveCount={result.archive_count}")
    print(f"expiresIn={result.expires_in}")
    print(result.launch_url)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
