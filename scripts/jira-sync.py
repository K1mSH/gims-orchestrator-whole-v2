#!/usr/bin/env python3
"""
Jira 동기화 스크립트
todo/system/*.md + jira-mapping.json → Jira Epic/Task/Sub-task 동기화

사용법:
  python scripts/jira-sync.py --init           # 초기 생성 (기존 삭제 + 새로 생성 + 매핑 저장)
  python scripts/jira-sync.py --sync           # 동기화 (매핑 기반 상태 업데이트 + 신규 생성)
  python scripts/jira-sync.py --dry-run --sync # 동기화 미리보기
"""

import os
import re
import json
import time
import argparse
import requests
from pathlib import Path

# === 설정 ===
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent
JIRA_DIR = PROJECT_ROOT / "dev_plan" / "jira"
TODO_DIR = PROJECT_ROOT / "todo" / "system"
MAPPING_FILE = JIRA_DIR / "jira-mapping.json"

SITE = "https://k1m-gims.atlassian.net"
PROJECT_KEY = "KAN"
EMAIL = open(JIRA_DIR / "jira_email.txt").read().strip()
TOKEN = open(JIRA_DIR / "jira_token.txt").read().strip()
AUTH = (EMAIL, TOKEN)

# 이슈 타입 ID
EPIC_TYPE = "10005"
TASK_TYPE = "10007"
SUBTASK_TYPE = "10006"

# 상태 전환 ID
TRANSITION_TODO = "11"
TRANSITION_IN_PROGRESS = "21"
TRANSITION_DONE = "41"

# 태그 → Jira 라벨
SKIP_TAGS = {"참고"}
LABEL_MAP = {
    "등록": "등록",
    "확정필요": "확정필요",
}

FILE_ORDER = [
    "01-datasource.md",
    "02-agent-pipeline.md",
    "03-api-collect.md",
    "04-monitoring.md",
    "05-api-provide.md",
    "06-procedure-mgmt.md",
    "07-security.md",
    "08-provide-agent.md",
]


# === 매핑 파일 ===

def load_mapping():
    if MAPPING_FILE.exists():
        with open(MAPPING_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {"epics": {}, "tasks": {}, "subtasks": {}}


def save_mapping(mapping):
    with open(MAPPING_FILE, "w", encoding="utf-8") as f:
        json.dump(mapping, f, ensure_ascii=False, indent=2)
    print(f"  매핑 저장: {MAPPING_FILE}")


# === 파서 ===

def parse_todo_file(filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        lines = f.readlines()

    epic_name = None
    epic_description_lines = []
    tasks = []
    current_task = None
    in_reference = False

    for line in lines:
        stripped = line.rstrip()

        if stripped.startswith("# ") and not stripped.startswith("## "):
            epic_name = stripped[2:].strip()
            continue

        if stripped.startswith("> "):
            epic_description_lines.append(stripped[2:].strip())
            continue

        if stripped.startswith("## 상태:"):
            continue

        if stripped == "---":
            continue

        match = re.match(r"^## (.+?) \[(.+?)\]\s*$", stripped)
        if match:
            title = match.group(1).strip()
            tag = match.group(2).strip()

            if tag in SKIP_TAGS:
                in_reference = True
                if current_task:
                    tasks.append(current_task)
                current_task = None
                continue

            in_reference = False
            if current_task:
                tasks.append(current_task)

            label = LABEL_MAP.get(tag, "개발")
            current_task = {
                "title": title,
                "tag": tag,
                "label": label,
                "subtasks": [],
            }
            continue

        if in_reference:
            if stripped:
                epic_description_lines.append(stripped)
            continue

        match = re.match(r"^- \[(x| )\] (.+)$", stripped)
        if match and current_task:
            done = match.group(1) == "x"
            text = match.group(2).strip()
            current_task["subtasks"].append({"title": text, "done": done})
            continue

    if current_task:
        tasks.append(current_task)

    return {
        "name": epic_name,
        "description": "\n".join(epic_description_lines) if epic_description_lines else "",
        "tasks": tasks,
    }


def parse_all():
    epics = []
    for filename in FILE_ORDER:
        filepath = TODO_DIR / filename
        if filepath.exists():
            epic = parse_todo_file(filepath)
            epic["file"] = filename
            epic["file_key"] = filename.replace(".md", "")
            epics.append(epic)
    return epics


# === Jira API ===

def jira_request(method, path, data=None):
    url = f"{SITE}/rest/api/3{path}"
    headers = {"Content-Type": "application/json"}
    for attempt in range(3):
        try:
            resp = requests.request(method, url, auth=AUTH, headers=headers, json=data, timeout=30)
            if resp.status_code == 429:
                wait = int(resp.headers.get("Retry-After", 5))
                print(f"    Rate limited, {wait}초 대기...")
                time.sleep(wait)
                continue
            return resp
        except requests.exceptions.RequestException:
            if attempt < 2:
                time.sleep(2)
    return None


def make_description_adf(text):
    """텍스트를 Jira ADF(Atlassian Document Format)로 변환"""
    if not text:
        return None
    return {
        "type": "doc",
        "version": 1,
        "content": [
            {
                "type": "codeBlock",
                "content": [{"type": "text", "text": text}]
            }
        ]
    }


def create_issue(issue_type, summary, parent_key=None, labels=None, description=None, start_date=None, end_date=None):
    fields = {
        "project": {"key": PROJECT_KEY},
        "issuetype": {"id": issue_type},
        "summary": summary,
    }
    if parent_key:
        fields["parent"] = {"key": parent_key}
    if labels:
        fields["labels"] = labels
    if description:
        fields["description"] = make_description_adf(description)
    if start_date:
        fields["customfield_10015"] = start_date  # Epic start date (Jira Cloud 기본)
    if end_date:
        fields["duedate"] = end_date

    resp = jira_request("POST", "/issue", {"fields": fields})
    if resp and resp.status_code == 201:
        return resp.json()
    else:
        print(f"    ERROR 생성 실패: {summary}")
        if resp:
            print(f"    {resp.status_code}: {resp.text[:300]}")
        return None


def transition_issue(issue_key, transition_id):
    resp = jira_request("POST", f"/issue/{issue_key}/transitions",
                        {"transition": {"id": transition_id}})
    return resp and resp.status_code in (200, 204)


def get_issue_status(issue_key):
    resp = jira_request("GET", f"/issue/{issue_key}?fields=status")
    if resp and resp.status_code == 200:
        return resp.json()["fields"]["status"]["name"]
    return None


def delete_all_issues():
    print("\n=== 기존 이슈 삭제 ===")
    all_ids = []
    next_token = None

    while True:
        url = f"/search/jql?jql=project+%3D+{PROJECT_KEY}&maxResults=100&fields=id"
        if next_token:
            url += f"&nextPageToken={next_token}"
        resp = jira_request("GET", url)
        data = resp.json()
        issues = data.get("issues", [])
        if not issues:
            break
        all_ids.extend([i["id"] for i in issues])
        if data.get("isLast", True):
            break
        next_token = data.get("nextPageToken")

    print(f"  삭제 대상: {len(all_ids)}건")

    deleted = 0
    for issue_id in reversed(all_ids):
        resp = jira_request("DELETE", f"/issue/{issue_id}?deleteSubtasks=true")
        if resp and resp.status_code in (204, 200):
            deleted += 1
            if deleted % 50 == 0:
                print(f"    {deleted}/{len(all_ids)} 삭제 완료...")
        time.sleep(0.1)

    print(f"  삭제 완료: {deleted}건")


# === 고아 이슈 정리 (--cleanup-orphans) ===

def cleanup_orphans(dry_run=False):
    """mapping.json에 없는 Jira 이슈를 찾아 삭제"""
    print("\n=== 고아 이슈 정리 ===")
    mapping = load_mapping()
    known_keys = set()
    for v in mapping["epics"].values():
        known_keys.add(v["key"])
    for v in mapping["tasks"].values():
        known_keys.add(v["key"])
    for v in mapping["subtasks"].values():
        known_keys.add(v["key"])
    print(f"  매핑 보유 key: {len(known_keys)}개")

    # Jira 전체 이슈 조회
    all_issues = []
    next_token = None
    while True:
        url = f"/search/jql?jql=project+%3D+{PROJECT_KEY}&maxResults=100&fields=summary,issuetype,status"
        if next_token:
            url += f"&nextPageToken={next_token}"
        resp = jira_request("GET", url)
        if not resp or resp.status_code != 200:
            print(f"  조회 실패: {resp.status_code if resp else 'N/A'}")
            return
        data = resp.json()
        issues = data.get("issues", [])
        if not issues:
            break
        all_issues.extend(issues)
        if data.get("isLast", True):
            break
        next_token = data.get("nextPageToken")

    print(f"  Jira 전체 이슈: {len(all_issues)}건")

    orphans = [i for i in all_issues if i["key"] not in known_keys]
    print(f"  고아 이슈: {len(orphans)}건")

    if not orphans:
        print("  정리할 항목 없음.")
        return

    orphans.sort(key=lambda x: int(x["key"].split("-")[1]))

    for i in orphans:
        issue_type = i["fields"]["issuetype"]["name"]
        status = i["fields"]["status"]["name"]
        summary = i["fields"]["summary"]
        print(f"    {i['key']:12} [{issue_type:8}] [{status}] {summary[:70]}")

    if dry_run:
        print("\n  [DRY RUN] 실제 삭제하지 않습니다.")
        return

    print(f"\n  실제 삭제 진행...")
    deleted = 0
    failed = 0
    for issue in orphans:
        key = issue["key"]
        resp = jira_request("DELETE", f"/issue/{key}?deleteSubtasks=true")
        if resp and resp.status_code in (204, 200):
            deleted += 1
            print(f"    삭제: {key}")
        else:
            failed += 1
            code = resp.status_code if resp else 'N/A'
            print(f"    실패: {key} ({code})")
        time.sleep(0.1)

    print(f"\n  삭제 완료: {deleted}건, 실패: {failed}건")


# === 초기 생성 (--init) ===

def init_create(epics, dry_run=False):
    print("\n=== 초기 생성 ===")
    mapping = {"epics": {}, "tasks": {}, "subtasks": {}}

    total_tasks = sum(len(e["tasks"]) for e in epics)
    total_subtasks = sum(sum(len(t["subtasks"]) for t in e["tasks"]) for e in epics)
    print(f"  생성 예정: Epic {len(epics)}개, Task {total_tasks}개, Sub-task {total_subtasks}개")

    if dry_run:
        print("\n  [DRY RUN] 실제 생성하지 않습니다.\n")
        for epic in epics:
            fk = epic["file_key"]
            print(f"  Epic: {epic['name']} ({fk})")
            for task in epic["tasks"]:
                tk = f"{fk}#{task['title']}"
                done_count = sum(1 for s in task["subtasks"] if s["done"])
                total = len(task["subtasks"])
                print(f"    Task: {task['title']} [{task['label']}] ({done_count}/{total})")
                for st in task["subtasks"]:
                    mark = "x" if st["done"] else " "
                    print(f"      [{mark}] {st['title']}")
        return

    stats = {"epics": 0, "tasks": 0, "subtasks": 0, "done": 0}

    for epic in epics:
        fk = epic["file_key"]
        print(f"\n  Epic: {epic['name']}")

        result = create_issue(EPIC_TYPE, epic["name"], description=epic.get("description") or None)
        if not result:
            continue
        epic_key = result["key"]
        stats["epics"] += 1
        mapping["epics"][fk] = {"key": epic_key, "startDate": None, "endDate": None}
        print(f"    → {epic_key}")
        time.sleep(0.2)

        for task in epic["tasks"]:
            tk = f"{fk}#{task['title']}"
            result = create_issue(TASK_TYPE, task["title"], parent_key=epic_key, labels=[task["label"]])
            if not result:
                continue
            task_key = result["key"]
            stats["tasks"] += 1
            mapping["tasks"][tk] = {"key": task_key, "startDate": None, "endDate": None}

            done_count = sum(1 for s in task["subtasks"] if s["done"])
            total = len(task["subtasks"])
            print(f"    Task: {task_key} — {task['title']} [{task['label']}] ({done_count}/{total})")
            time.sleep(0.2)

            for st in task["subtasks"]:
                sk = f"{tk}#{st['title']}"
                result = create_issue(SUBTASK_TYPE, st["title"], parent_key=task_key)
                if not result:
                    continue
                st_key = result["key"]
                stats["subtasks"] += 1
                mapping["subtasks"][sk] = {"key": st_key}

                if st["done"]:
                    if transition_issue(st_key, TRANSITION_DONE):
                        stats["done"] += 1
                time.sleep(0.1)

            if total > 0 and done_count == total:
                transition_issue(task_key, TRANSITION_DONE)

    save_mapping(mapping)

    print(f"\n=== 초기 생성 완료 ===")
    print(f"  Epic: {stats['epics']}개, Task: {stats['tasks']}개, Sub-task: {stats['subtasks']}개")
    print(f"  완료 전환: {stats['done']}건")


# === 동기화 (--sync) ===

def sync(epics, dry_run=False, cleanup=True):
    print("\n=== 동기화 ===")
    mapping = load_mapping()
    if not mapping["epics"]:
        print("  매핑 파일 없음. --init 먼저 실행하세요.")
        return

    stats = {"created": 0, "updated": 0, "skipped": 0}

    for epic in epics:
        fk = epic["file_key"]
        epic_info = mapping["epics"].get(fk)

        if not epic_info:
            print(f"  [신규 Epic] {epic['name']} — 생성 필요")
            if not dry_run:
                result = create_issue(EPIC_TYPE, epic["name"], description=epic.get("description") or None)
                if result:
                    epic_info = {"key": result["key"], "startDate": None, "endDate": None}
                    mapping["epics"][fk] = epic_info
                    stats["created"] += 1
                    print(f"    → {result['key']}")
                    time.sleep(0.2)
            else:
                print(f"    [DRY] 생성 예정")
            if not epic_info:
                continue

        epic_key = epic_info["key"]
        print(f"\n  Epic: {epic['name']} ({epic_key})")

        for task in epic["tasks"]:
            tk = f"{fk}#{task['title']}"
            task_info = mapping["tasks"].get(tk)

            if not task_info:
                print(f"    [신규 Task] {task['title']}")
                if not dry_run:
                    result = create_issue(TASK_TYPE, task["title"], parent_key=epic_key, labels=[task["label"]])
                    if result:
                        task_info = {"key": result["key"], "startDate": None, "endDate": None}
                        mapping["tasks"][tk] = task_info
                        stats["created"] += 1
                        time.sleep(0.2)
                else:
                    print(f"      [DRY] 생성 예정")
                if not task_info:
                    continue

            task_key = task_info["key"]
            done_count = sum(1 for s in task["subtasks"] if s["done"])
            total = len(task["subtasks"])

            for st in task["subtasks"]:
                sk = f"{tk}#{st['title']}"
                st_info = mapping["subtasks"].get(sk)

                if not st_info:
                    print(f"      [신규] {st['title']}")
                    if not dry_run:
                        result = create_issue(SUBTASK_TYPE, st["title"], parent_key=task_key)
                        if result:
                            st_info = {"key": result["key"]}
                            mapping["subtasks"][sk] = st_info
                            stats["created"] += 1
                            if st["done"]:
                                transition_issue(result["key"], TRANSITION_DONE)
                            time.sleep(0.1)
                    else:
                        print(f"        [DRY] 생성 예정")
                    continue

                # 기존 Sub-task 상태 동기화
                st_key = st_info["key"]
                if dry_run:
                    current = get_issue_status(st_key)
                    expected = "완료" if st["done"] else "해야 할 일"
                    if current != expected:
                        print(f"      [상태변경] {st_key} {st['title']}: {current} → {expected}")
                        stats["updated"] += 1
                    else:
                        stats["skipped"] += 1
                else:
                    current = get_issue_status(st_key)
                    if st["done"] and current != "완료":
                        transition_issue(st_key, TRANSITION_DONE)
                        print(f"      [완료] {st_key} {st['title']}")
                        stats["updated"] += 1
                    elif not st["done"] and current == "완료":
                        transition_issue(st_key, TRANSITION_TODO)
                        print(f"      [복원] {st_key} {st['title']}")
                        stats["updated"] += 1
                    else:
                        stats["skipped"] += 1
                    time.sleep(0.05)

            # Task 상태: 전부 완료면 Task도 완료
            if not dry_run and total > 0:
                task_status = get_issue_status(task_key)
                if done_count == total and task_status != "완료":
                    transition_issue(task_key, TRANSITION_DONE)
                    print(f"    [Task 완료] {task_key} {task['title']}")
                elif done_count < total and task_status == "완료":
                    transition_issue(task_key, TRANSITION_TODO)
                    print(f"    [Task 복원] {task_key} {task['title']}")

    if not dry_run:
        save_mapping(mapping)

    print(f"\n=== 동기화 완료 ===")
    print(f"  신규 생성: {stats['created']}건")
    print(f"  상태 변경: {stats['updated']}건")
    print(f"  변경 없음: {stats['skipped']}건")

    if cleanup:
        cleanup_orphans(dry_run=dry_run)


# === 메인 ===

def main():
    parser = argparse.ArgumentParser(description="Jira 동기화 스크립트")
    parser.add_argument("--init", action="store_true", help="초기 생성 (기존 삭제 + 생성 + 매핑 저장)")
    parser.add_argument("--sync", action="store_true", help="동기화 (매핑 기반 업데이트)")
    parser.add_argument("--dry-run", action="store_true", help="실제 호출 없이 미리보기")
    parser.add_argument("--delete-all", action="store_true", help="기존 이슈 전부 삭제만")
    parser.add_argument("--cleanup-orphans", action="store_true", help="mapping에 없는 고아 이슈 삭제 (단독 실행)")
    parser.add_argument("--no-cleanup", action="store_true", help="--sync 시 자동 고아 정리 비활성화")
    args = parser.parse_args()

    if not any([args.init, args.sync, args.delete_all, args.cleanup_orphans]):
        parser.print_help()
        return

    if args.cleanup_orphans:
        cleanup_orphans(dry_run=args.dry_run)
        return

    print("=== todo/system 파싱 ===")
    epics = parse_all()
    for e in epics:
        task_count = len(e["tasks"])
        sub_count = sum(len(t["subtasks"]) for t in e["tasks"])
        print(f"  {e['file']} → {e['name']} (Task {task_count}, Sub {sub_count})")

    if args.delete_all:
        if not args.dry_run:
            delete_all_issues()
        else:
            print("\n  [DRY RUN] 삭제 스킵")

    if args.init:
        if not args.dry_run:
            delete_all_issues()
        init_create(epics, dry_run=args.dry_run)

    if args.sync:
        sync(epics, dry_run=args.dry_run, cleanup=not args.no_cleanup)


if __name__ == "__main__":
    main()
