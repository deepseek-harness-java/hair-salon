#!/usr/bin/env python3
"""hair-salon E2E：通过业务应用 SSE 代理调用 DSH Agent，验证 5 个工具全链路。"""
import json, subprocess, sys

AGENT = "salon-copilot"
URL = "http://127.0.0.1:18110/api/assistant/stream"

CASES = [
    ("T1 价目查询", "理发店剪头发多少钱？烫发什么价？简洁回答", ["精剪", "烫发"]),
    ("T2 发型师查询", "理发店哪位发型师评分最高？擅长什么？简洁回答", ["小雅", "染发"]),
    ("T3 预约下单", "我是测试顾客赵敏，电话13700003333，想约理发店资深发型师染发，周四下午 2 点，帮我预约，告诉我预约号和价格", ["H1", "染发"]),
    ("T4 预约查询", "查一下理发店预约单 H1001，谁约的？什么项目？简洁回答", ["周先生", "精剪"]),
    ("T5 运营统计", "理发店今天运营情况怎么样？有多少预约？简洁回答", ["预约", "营收"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== hair-salon E2E: {passed}/{len(cases)} PASS =====")
    if failed:
        print("失败用例:", ", ".join(failed))
        sys.exit(1)

if __name__ == "__main__":
    main()
