"""근거 검색 기준선 평가 실행기 (IKA-9).

평가 자료(JSONL)는 운영 원문을 담으므로 저장소 밖에 둔다. 이 스크립트는 저장소 안 경로의 평가 자료를 거부한다.
관리자 세션 쿠키는 환경 변수로만 받는다(명령줄 인자·로그·결과 파일에 남기지 않는다).
결과 파일에는 집계 숫자만 쓴다. 질문 원문, 근거 ID, 게시글 ID는 쓰지 않는다.

사용 예:
  set SNOWTHING_EVAL_COOKIE=JSESSIONID=...   (PowerShell: $env:SNOWTHING_EVAL_COOKIE = "JSESSIONID=...")
  python -I scripts/evaluate-evidence-search.py --eval-set D:/private/qna-eval.jsonl \
      --base-url http://localhost:8080 --split eval --output docs/conception/017_ai_answer_assist/eval-result.json

평가 자료 한 줄 형식:
  {"id": "q01", "split": "eval", "question": "...", "askedAt": "2026-10-15T09:30:00",
   "questionPostPublicId": "uuid", "expectedEvidenceIds": ["P:12:1", "C:40:0"], "reviewedBy": "ikae"}
  - expectedEvidenceIds가 빈 목록이면 "답이 되는 근거가 없음"으로 라벨링한 질문이다.
  - query가 있으면 question 대신 검색어로 쓴다. 검색 API 제한(100자)을 넘으면 앞 100자로 자르고 잘린 건수를 기록한다.
  - reviewedBy가 비어 있으면 라벨링 전으로 보고 집계에서 빼고 건수만 센다.
"""
import argparse
import datetime
import hashlib
import json
import os
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

KS = (1, 3, 5, 10)
MAX_QUERY_CODE_POINTS = 100
SPLITS = ('tuning', 'eval')
EVIDENCE_ID = re.compile(r'^[PC]:[1-9][0-9]{0,18}:(0|[1-9][0-9]{0,3})$')
PUBLIC_ID = re.compile(r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$')
ASKED_AT = re.compile(r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$')
REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent


class EvalSetError(ValueError):
    pass


def load_eval_set(path):
    items = []
    with open(path, encoding='utf-8') as source:
        for line_no, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                item = json.loads(line)
            except json.JSONDecodeError as error:
                raise EvalSetError(f'line {line_no}: invalid JSON ({error.msg})') from None
            validate(item, line_no)
            items.append(item)
    ids = [item['id'] for item in items]
    if len(ids) != len(set(ids)):
        raise EvalSetError('duplicate id')
    return items


def validate(item, line_no):
    def fail(field):
        # 값 자체는 출력하지 않는다(질문 원문이 섞일 수 있다).
        raise EvalSetError(f'line {line_no}: invalid or missing field "{field}"')

    if not isinstance(item.get('id'), str) or not item['id'].strip():
        fail('id')
    if item.get('split') not in SPLITS:
        fail('split')
    if not isinstance(item.get('question'), str) or len(item['question'].strip()) < 2:
        fail('question')
    if 'query' in item and (not isinstance(item['query'], str) or len(item['query'].strip()) < 2):
        fail('query')
    asked_at = item.get('askedAt')
    if not isinstance(asked_at, str) or not ASKED_AT.match(asked_at):
        fail('askedAt')
    try:
        datetime.datetime.strptime(asked_at, '%Y-%m-%dT%H:%M:%S')
    except ValueError:
        fail('askedAt')
    if not isinstance(item.get('questionPostPublicId'), str) or not PUBLIC_ID.match(item['questionPostPublicId']):
        fail('questionPostPublicId')
    expected = item.get('expectedEvidenceIds')
    if not isinstance(expected, list) or not all(isinstance(e, str) and EVIDENCE_ID.match(e) for e in expected):
        fail('expectedEvidenceIds')
    if len(expected) != len(set(expected)):
        fail('expectedEvidenceIds')
    if 'reviewedBy' not in item or not isinstance(item['reviewedBy'], (str, type(None))):
        fail('reviewedBy')


def search_query(item):
    text = (item.get('query') or item['question']).strip()
    code_points = list(text)
    if len(code_points) > MAX_QUERY_CODE_POINTS:
        return ''.join(code_points[:MAX_QUERY_CODE_POINTS]).strip(), True
    return text, False


class _RefuseRedirect(urllib.request.HTTPRedirectHandler):
    """리디렉션을 따라가지 않는다. 따라가면 urllib이 Cookie 헤더를 다른 호스트로도 보낸다."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


NO_REDIRECT_OPENER = urllib.request.build_opener(_RefuseRedirect)


def call_search(base_url, cookie, query, item, size, timeout):
    params = urllib.parse.urlencode({
        'q': query,
        'size': size,
        'createdBefore': item['askedAt'],
        'excludePostPublicId': item['questionPostPublicId'],
    })
    request = urllib.request.Request(
        f'{base_url.rstrip("/")}/api/v1/admin/search/evidence?{params}',
        headers={'Cookie': cookie, 'Accept': 'application/json'},
        method='GET')
    started = time.perf_counter()
    try:
        with NO_REDIRECT_OPENER.open(request, timeout=timeout) as response:
            body = json.loads(response.read().decode('utf-8'))
            status = response.status
    except urllib.error.HTTPError as error:
        return error.code, None, (time.perf_counter() - started) * 1000
    except (urllib.error.URLError, TimeoutError):
        return 'network', None, (time.perf_counter() - started) * 1000
    except (json.JSONDecodeError, UnicodeDecodeError):
        # 세션 만료 시 로그인 HTML처럼 200이지만 JSON이 아닌 응답이 올 수 있다. 해당 항목만 오류로 센다.
        return 'invalid_json', None, (time.perf_counter() - started) * 1000
    if not isinstance(body, dict) or not isinstance(body.get('items'), list):
        return 'invalid_json', None, (time.perf_counter() - started) * 1000
    elapsed_ms = (time.perf_counter() - started) * 1000
    return status, [entry['evidenceId'] for entry in body.get('items', [])], elapsed_ms


def percentile(values, p):
    """nearest-rank"""
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, -(-p * len(ordered) // 100))
    return round(ordered[int(rank) - 1], 1)


def ratio(n, total):
    return {'n': n, 'N': total, 'text': f'{n}/{total}'}


def evaluate(items, base_url, cookie, timeout):
    size = max(KS)
    answerable = [i for i in items if i['expectedEvidenceIds']]
    no_answer = [i for i in items if not i['expectedEvidenceIds']]
    hit = {k: 0 for k in KS}
    found_gold = {k: 0 for k in KS}
    total_gold = sum(len(i['expectedEvidenceIds']) for i in answerable)
    no_answer_with_top1 = 0
    latencies = []
    errors = {}
    truncated = 0

    for item in answerable + no_answer:
        query, was_truncated = search_query(item)
        truncated += was_truncated
        status, ranked, elapsed_ms = call_search(base_url, cookie, query, item, size, timeout)
        if ranked is None:
            errors[str(status)] = errors.get(str(status), 0) + 1
            continue
        latencies.append(elapsed_ms)
        if item['expectedEvidenceIds']:
            gold = set(item['expectedEvidenceIds'])
            for k in KS:
                matched = gold.intersection(ranked[:k])
                hit[k] += 1 if matched else 0
                found_gold[k] += len(matched)
        elif ranked:
            no_answer_with_top1 += 1

    return {
        'answerable': {
            'count': len(answerable),
            'hitAtK': {str(k): ratio(hit[k], len(answerable)) for k in KS},
            'recallAtK': {str(k): ratio(found_gold[k], total_gold) for k in KS},
        },
        'noAnswer': {
            'count': len(no_answer),
            'withTop1Result': ratio(no_answer_with_top1, len(no_answer)),
        },
        'latencyMs': {
            'n': len(latencies),
            'p50': percentile(latencies, 50),
            'p95': percentile(latencies, 95),
        },
        'errorsByStatus': errors,
        'truncatedQueries': truncated,
    }


def main():
    parser = argparse.ArgumentParser(description='근거 검색 기준선 평가 (집계만 출력)')
    parser.add_argument('--eval-set', required=True, help='저장소 밖 JSONL 경로')
    parser.add_argument('--base-url', default='http://localhost:8080')
    parser.add_argument('--split', choices=('eval', 'tuning', 'all'), default='eval')
    parser.add_argument('--cookie-env', default='SNOWTHING_EVAL_COOKIE',
                        help='관리자 세션 Cookie 헤더 값을 담은 환경 변수 이름')
    parser.add_argument('--output', required=True, help='집계 JSON을 쓸 경로')
    parser.add_argument('--label', default='',
                        help='결과에 붙일 설명. 예: "harness check, not a measurement"')
    parser.add_argument('--timeout', type=float, default=10.0)
    args = parser.parse_args()

    eval_path = pathlib.Path(args.eval_set).resolve()
    if eval_path == REPO_ROOT or REPO_ROOT in eval_path.parents:
        parser.error('평가 자료는 저장소 밖에 두어야 한다(공개 저장소에 원문을 커밋하지 않기 위해)')
    cookie = os.environ.get(args.cookie_env, '').strip()
    if not cookie:
        parser.error(f'환경 변수 {args.cookie_env}에 관리자 세션 Cookie 값을 넣어야 한다')

    try:
        all_items = load_eval_set(eval_path)
    except EvalSetError as error:
        parser.error(f'평가 자료 형식 오류: {error}')
    reviewed = [i for i in all_items if (i.get('reviewedBy') or '').strip()]
    selected = [i for i in reviewed if args.split == 'all' or i['split'] == args.split]

    result = {
        'label': args.label,
        'evaluatedAt': datetime.datetime.now().isoformat(timespec='seconds'),
        'baseUrl': args.base_url,
        'evalSetSha256': hashlib.sha256(eval_path.read_bytes()).hexdigest(),
        'split': args.split,
        'items': {
            'total': len(all_items),
            'unreviewedSkipped': len(all_items) - len(reviewed),
            'selected': len(selected),
            'bySplit': {s: sum(1 for i in reviewed if i['split'] == s) for s in SPLITS},
        },
        'k': list(KS),
        'definitions': {
            'hitAtK': '정답 근거가 하나라도 상위 k에 든 답변 가능 질문 수 / 답변 가능 질문 수',
            'recallAtK': '상위 k에 든 정답 근거 수 / 전체 정답 근거 수',
            'noAnswer.withTop1Result': '근거 없음으로 라벨링한 질문 중 검색 결과가 1건 이상 나온 수 (정밀도 주장 아님)',
            'latencyMs': '이 스크립트에서 잰 HTTP 왕복 시간(nearest-rank)',
            'searchFilters': 'createdBefore=askedAt, excludePostPublicId=questionPostPublicId',
        },
        **evaluate(selected, args.base_url, cookie, args.timeout),
    }

    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    answerable = result['answerable']
    print(f"selected={result['items']['selected']} answerable={answerable['count']} "
          f"noAnswer={result['noAnswer']['count']} errors={result['errorsByStatus']}")
    for k in KS:
        print(f"hit@{k}={answerable['hitAtK'][str(k)]['text']} recall@{k}={answerable['recallAtK'][str(k)]['text']}")
    print(f"noAnswer withTop1Result={result['noAnswer']['withTop1Result']['text']} "
          f"latency p50={result['latencyMs']['p50']}ms p95={result['latencyMs']['p95']}ms")
    return 0


if __name__ == '__main__':
    sys.exit(main())
