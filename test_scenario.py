# -*- coding: utf-8 -*-
"""
Полный сценарий E2E. Запуск: python test_scenario.py
Требует поднятый docker compose.
"""
import sys
import json
import time
import urllib.request
import urllib.error
import uuid

# Windows-консоль cp1251 не вытянет unicode без этого
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass

BASE = "http://localhost"


def call(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json; charset=utf-8"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8") or "{}")


PASS, FAIL = 0, 0


def section(title):
    print("\n=== " + title + " ===")


def check(cond, msg):
    global PASS, FAIL
    if cond:
        print("  [OK] " + msg)
        PASS += 1
    else:
        print("  [FAIL] " + msg)
        FAIL += 1


def assertion(label, expected, actual):
    check(expected == actual, f"{label}: ожидалось {expected!r}, получено {actual!r}")


# ────────── USER 1: ИДЕАЛЬНЫЙ ПУТЬ ──────────
section("USER 1: регистрация")
suffix = uuid.uuid4().hex[:6]
status, r = call("POST", "/api/register", {
    "email": f"hero_{suffix}@test.com",
    "password": "qwerty123",
    "agent_name": "Герой",
})
check(status in (200, 201) and "token" in r, f"Регистрация ({status}): {r}")
token = r["token"]

section("USER 1: старт дела (демо)")
status, r = call("POST", "/api/case/start", {"case_id": "case_001"}, token)
assertion("HTTP", 200, status)
assertion("paid", False, r["paid"])
assertion("docs.len (demo)", 12, len(r["docs"]))
assertion("min_evidences_for_accusation", 5, r["case"]["min_evidences_for_accusation"])
assertion("required_doc_count", 12, r["required_doc_count"])
check(len(r["available_evidences"]) == 0, f"available_evidences пуст изначально: {len(r['available_evidences'])}")

section("USER 1: попытка обвинения без оплаты")
status, r = call("POST", "/api/case/accuse", {
    "case_id": "case_001", "suspect": "Марина Волкова", "selected_evidences": []
}, token)
assertion("result", "demo_end", r.get("result"))

section("USER 1: оплата — payment/init + payment/confirm")
status, r = call("POST", "/api/payment/init", {"case_id": "case_001"}, token)
check("payment_url" in r, f"payment_url есть: {r}")
status, r = call("POST", "/api/payment/confirm", {"case_id": "case_001"}, token)
assertion("оплата success", True, r.get("success"))

section("USER 1: старт дела после оплаты — должно быть 20 docs")
status, r = call("POST", "/api/case/start", {"case_id": "case_001"}, token)
assertion("paid", True, r["paid"])
assertion("docs.len (full)", 20, len(r["docs"]))
all_doc_ids = [d["id"] for d in r["docs"]]

section("USER 1: попытка обвинения до открытия документов → needs_investigation")
status, r = call("POST", "/api/case/accuse", {
    "case_id": "case_001", "suspect": "Марина Волкова", "selected_evidences": []
}, token)
assertion("result", "needs_investigation", r.get("result"))
check(len(r.get("missing_docs", [])) == 20, f"missing_docs = {len(r.get('missing_docs', []))}")

section("USER 1: открываю все 20 документов")
last_evidences = []
for did in all_doc_ids:
    s, rr = call("POST", "/api/case/open_doc", {"case_id": "case_001", "doc_id": did}, token)
    last_evidences = rr["available_evidences"]
print(f"  улик после открытия всех документов: {len(last_evidences)}")

section("USER 1: попытка обвинения без улик → needs_evidence")
status, r = call("POST", "/api/case/accuse", {
    "case_id": "case_001", "suspect": "Марина Волкова", "selected_evidences": []
}, token)
assertion("result", "needs_evidence", r.get("result"))

# Соберём улики на Марину Волкову
ev_volkova = [e["id"] for e in last_evidences if e.get("points_to") == "Марина Волкова"]
ev_semenov = [e["id"] for e in last_evidences if e.get("points_to") == "Игорь Семёнов"]
ev_neutral = [e["id"] for e in last_evidences if e.get("points_to") is None]
print(f"  на Волкову: {len(ev_volkova)} улик")
print(f"  на Семёнова: {len(ev_semenov)} улик")
print(f"  нейтральных: {len(ev_neutral)} улик")

section("USER 1: обвинение Волковой с уликами на Семёнова → wrong_evidence")
status, r = call("POST", "/api/case/accuse", {
    "case_id": "case_001", "suspect": "Марина Волкова",
    "selected_evidences": (ev_volkova[:3] + ev_semenov[:2])
}, token)
assertion("result", "wrong_evidence", r.get("result"))

section("USER 1: обвинение Волковой со ВСЕМИ её уликами → correct + evidence_master")
status, r = call("POST", "/api/case/accuse", {
    "case_id": "case_001", "suspect": "Марина Волкова",
    "selected_evidences": ev_volkova
}, token)
assertion("result", "correct", r.get("result"))
print(f"  rank: {r.get('rank')}")

section("USER 1: эпилог")
status, r = call("GET", "/api/case/case_001/epilogue", token=token)
assertion("HTTP", 200, status)
print(f"  headline: {r.get('headline')}")
print(f"  timeline: {len(r.get('timeline', []))} точек, clue_breakdown: {len(r.get('clue_breakdown', []))} разборов")
print(f"  official_misses: {len(r.get('official_misses', []))}")
check(r.get("answer") == "Марина Волкова", "answer == Марина Волкова")

section("USER 1: профиль — достижения")
status, r = call("GET", "/api/profile", token=token)
earned = [a["achievement_key"] for a in r["achievements"] if a["earned"]]
print(f"  {r['achievements_earned']} / {r['achievements_total']}")
print(f"  получено: {earned}")
expected_for_user1 = {"intuition", "case_solved", "marathoner", "paid_path", "evidence_master"}
# careful — может зависеть, был ли early_accuse_attempt; в этом сценарии он БЫЛ → careful не должен прийти
got = set(earned)
missing = expected_for_user1 - got
extra = got - expected_for_user1
check(not missing, f"Ожидаемые есть: {expected_for_user1 - missing}, отсутствуют: {missing}")
print(f"  лишние (необязательно баг): {extra}")

# ────────── USER 2: НЕГАТИВНЫЕ ВЕТКИ ──────────
section("USER 2: чистая регистрация")
status, r = call("POST", "/api/register", {
    "email": f"clean_{suffix}@test.com",
    "password": "qwerty123",
    "agent_name": "Чистый",
})
token2 = r["token"]

section("USER 2: оплата без открытия → потом обвинение → needs_investigation")
call("POST", "/api/case/start", {"case_id": "case_001"}, token2)
call("POST", "/api/payment/confirm", {"case_id": "case_001"}, token2)
status, r = call("POST", "/api/case/accuse", {
    "case_id": "case_001", "suspect": "Марина Волкова", "selected_evidences": []
}, token2)
assertion("result", "needs_investigation", r.get("result"))

section("USER 2: открываю все 20 → собираю улики на Волкову → correct")
status, r = call("POST", "/api/case/start", {"case_id": "case_001"}, token2)
all_doc_ids = [d["id"] for d in r["docs"]]
last_evidences = []
for did in all_doc_ids:
    _, rr = call("POST", "/api/case/open_doc", {"case_id": "case_001", "doc_id": did}, token2)
    last_evidences = rr["available_evidences"]
ev_volkova = [e["id"] for e in last_evidences if e.get("points_to") == "Марина Волкова"]

# берём ровно 5 (минимум)
status, r = call("POST", "/api/case/accuse", {
    "case_id": "case_001", "suspect": "Марина Волкова", "selected_evidences": ev_volkova[:5]
}, token2)
assertion("result (USER2 минимум 5)", "correct", r.get("result"))

section("USER 2: профиль")
_, r = call("GET", "/api/profile", token=token2)
earned = [a["achievement_key"] for a in r["achievements"] if a["earned"]]
print(f"  {r['achievements_earned']} / {r['achievements_total']}: {earned}")
# у USER 2 был early_accuse_attempt → careful НЕ должен быть
check("careful" not in earned, "USER 2 НЕ получил careful (был early_accuse_attempt)")
# У USER 2 не выбраны ВСЕ улики Волковой → evidence_master НЕ должен быть
check("evidence_master" not in earned, "USER 2 НЕ получил evidence_master (не все улики)")
check("intuition" in earned, "USER 2 получил intuition (raskryl c первой попытки accuse-correct)")

# ────────── USER 3: careful path ──────────
section("USER 3: регистрация (path для careful — никаких early попыток)")
status, r = call("POST", "/api/register", {
    "email": f"careful_{suffix}@test.com",
    "password": "qwerty123",
    "agent_name": "Аккуратный",
})
token3 = r["token"]

call("POST", "/api/case/start", {"case_id": "case_001"}, token3)
call("POST", "/api/payment/confirm", {"case_id": "case_001"}, token3)
status, r = call("POST", "/api/case/start", {"case_id": "case_001"}, token3)
all_doc_ids = [d["id"] for d in r["docs"]]

# Открываем ВСЕ документы СРАЗУ, без попыток accuse
last_evidences = []
for did in all_doc_ids:
    _, rr = call("POST", "/api/case/open_doc", {"case_id": "case_001", "doc_id": did}, token3)
    last_evidences = rr["available_evidences"]

ev_volkova = [e["id"] for e in last_evidences if e.get("points_to") == "Марина Волкова"]
status, r = call("POST", "/api/case/accuse", {
    "case_id": "case_001", "suspect": "Марина Волкова", "selected_evidences": ev_volkova[:5]
}, token3)
assertion("USER 3 result", "correct", r.get("result"))

_, r = call("GET", "/api/profile", token=token3)
earned3 = [a["achievement_key"] for a in r["achievements"] if a["earned"]]
print(f"  USER 3 достижений: {r['achievements_earned']} / {r['achievements_total']}")
print(f"  получено: {earned3}")
check("careful" in earned3, "USER 3 ПОЛУЧИЛ careful (без early попыток)")
check("intuition" in earned3, "USER 3 получил intuition")
check("paid_path" in earned3, "USER 3 получил paid_path")
check("marathoner" in earned3, "USER 3 получил marathoner")

print(f"\n{'='*40}\nИТОГО: PASS={PASS}  FAIL={FAIL}\n{'='*40}")
sys.exit(1 if FAIL else 0)
