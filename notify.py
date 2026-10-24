#!/usr/bin/env python3
"""Bark 双通道推送（自建服务端 + 官方服务端）。

环境变量:
    BARK_KEY_SELF      自建服务端 key（https://bark.nosr.top）
    BARK_KEY_OFFICIAL  官方服务端 key（https://api.day.app）

两个都没配置时静默跳过，绝不影响签到主流程。
"""

import logging
import os
import urllib.parse

import requests

logger = logging.getLogger(__name__)

# (显示名, 服务端根地址, 对应环境变量)
SERVERS = (
    ("自建", "https://bark.nosr.top", "BARK_KEY_SELF"),
    ("官方", "https://api.day.app", "BARK_KEY_OFFICIAL"),
)

TIMEOUT = 15
ICON = "https://nosr.top/favicon.ico"


def _send_one(label: str, base: str, key: str, payload: dict) -> bool:
    """先试 POST JSON，失败再退回 GET 路径形式。"""
    try:
        resp = requests.post(f"{base}/{key}", json=payload, timeout=TIMEOUT)
        data = resp.json() if resp.content else {}
        if resp.ok and data.get("code") == 200:
            logger.info(f"{label} 推送成功")
            return True
        logger.debug(f"{label} POST 未成功(HTTP {resp.status_code} {data})，改用 GET")
    except Exception as e:
        logger.debug(f"{label} POST 异常: {e}，改用 GET")

    try:
        quoted = "/".join(urllib.parse.quote(str(payload[k]), safe="") for k in ("title", "body"))
        query = urllib.parse.urlencode(
            {"level": payload["level"], "group": payload["group"]}
        )
        resp = requests.get(f"{base}/{key}/{quoted}?{query}", timeout=TIMEOUT)
        data = resp.json() if resp.content else {}
        if resp.ok and data.get("code") == 200:
            logger.info(f"{label} 推送成功(GET)")
            return True
        logger.warning(f"{label} 推送失败: HTTP {resp.status_code} {data}")
    except Exception as e:
        logger.warning(f"{label} 推送异常: {e}")
    return False


def send(title: str, body: str, level: str = "active", group: str = "贴吧签到") -> bool:
    """向所有已配置的通道推送，任一成功即返回 True。"""
    payload = {"title": title, "body": body, "level": level, "group": group, "icon": ICON}
    sent = False
    configured = False
    for label, base, env in SERVERS:
        key = (os.environ.get(env) or "").strip()
        if not key:
            logger.info(f"跳过 {label} 推送（未配置 {env}）")
            continue
        configured = True
        if _send_one(label, base, key, payload):
            sent = True
    if not configured:
        logger.info("未配置任何 Bark 通道，跳过推送")
    elif not sent:
        logger.warning("所有 Bark 通道推送均失败")
    return sent
