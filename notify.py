#!/usr/bin/env python3
"""Bark 双通道推送（自建服务端 + 官方服务端）。

环境变量:
    BARK_KEY_SELF       自建服务端 key（服务端地址由 BARK_HOST_SELF 指定）
    BARK_KEY_OFFICIAL   官方服务端 key
    BARK_HOST_SELF      自建服务端根地址，默认 https://bark.nosr.top
                        （GitHub Actions 的境外 IP 会被自建域名的 WAF 地理质询拦住，
                         在 workflow 里可指向 https://nosr-bark.nosr.workers.dev 绕过）

两个 key 都没配置时静默跳过，绝不影响签到主流程。
"""

import logging
import os
import urllib.parse

import requests

logger = logging.getLogger(__name__)

# (显示名, 地址环境变量, 默认地址, key 环境变量)
SERVERS = (
    ("自建", "BARK_HOST_SELF", "https://bark.nosr.top", "BARK_KEY_SELF"),
    ("官方", "BARK_HOST_OFFICIAL", "https://api.day.app", "BARK_KEY_OFFICIAL"),
)

TIMEOUT = 15
ICON = "https://nosr.top/favicon.ico"


def _send_one(label: str, base: str, key: str, payload: dict) -> tuple[bool, str]:
    """先试 POST JSON，失败再退回 GET 路径形式。返回 (是否成功, 失败原因)。"""
    base = base.rstrip("/")
    reason = ""
    try:
        resp = requests.post(f"{base}/{key}", json=payload, timeout=TIMEOUT)
        data = resp.json() if resp.content else {}
        if resp.ok and data.get("code") == 200:
            return True, ""
        reason = f"POST HTTP {resp.status_code} {str(data)[:120]}"
    except Exception as exc:
        reason = f"POST {type(exc).__name__}: {exc}"

    try:
        quoted = "/".join(
            urllib.parse.quote(str(payload[k]), safe="") for k in ("title", "body")
        )
        query = urllib.parse.urlencode(
            {"level": payload["level"], "group": payload["group"]}
        )
        resp = requests.get(f"{base}/{key}/{quoted}?{query}", timeout=TIMEOUT)
        data = resp.json() if resp.content else {}
        if resp.ok and data.get("code") == 200:
            return True, ""
        return False, f"{reason}；GET HTTP {resp.status_code} {str(data)[:120]}"
    except Exception as exc:
        return False, f"{reason}；GET {type(exc).__name__}: {exc}"


def send(title: str, body: str, level: str = "active", group: str = "贴吧签到") -> bool:
    """向所有已配置的通道推送，任一成功即返回 True。"""
    payload = {"title": title, "body": body, "level": level, "group": group, "icon": ICON}
    sent, configured, failures = False, False, []
    for label, host_env, default_host, key_env in SERVERS:
        key = (os.environ.get(key_env) or "").strip()
        if not key:
            logger.info(f"跳过 {label} 推送（未配置 {key_env}）")
            continue
        configured = True
        base = (os.environ.get(host_env) or default_host).strip()
        ok, reason = _send_one(label, base, key, payload)
        if ok:
            logger.info(f"{label} 推送成功（{base}）")
            sent = True
        else:
            failures.append(f"{label}({base}): {reason}")

    if not configured:
        logger.info("未配置任何 Bark 通道，跳过推送")
    elif sent:
        for f in failures:
            logger.info(f"另有通道未送达，已被其他通道覆盖 — {f}")
    else:
        logger.warning("所有 Bark 通道推送均失败 — " + "；".join(failures))
    return sent
