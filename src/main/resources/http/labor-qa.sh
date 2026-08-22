#!/bin/bash
# 근로기준법·세무 Q&A 테스트 (매장 무관 전역 기능)
# 사용법: ./labor-qa.sh {accessToken} [adminIngestToken]
#
# 지식베이스 적재는 기동 시 자동으로 돌지 않는다. 문서를 고쳤을 때 관리자가 직접 부른다.
# adminIngestToken은 서버의 ADMIN_INGEST_TOKEN 환경변수와 같아야 하며,
# 서버에 그 값이 없으면 적재 API 자체가 닫혀 있다.
BASE_URL="http://localhost:8080"
TOKEN="$1"
ADMIN_TOKEN="$2"

if [ -n "$ADMIN_TOKEN" ]; then
  echo "== 0. 지식베이스 적재 (관리자) =="
  echo "   청크 ID가 내용 기반이라 여러 번 불러도 중복되지 않고 덮어쓴다."
  curl -s -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "X-Admin-Token: $ADMIN_TOKEN" \
    "$BASE_URL/api/v1/labor-qa/admin/ingest" | python3 -m json.tool
  echo ""
fi

echo "== 1. 단발 질문 (대화 이력 없음) =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"주휴수당은 언제 받을 수 있나요?"}' \
  "$BASE_URL/api/v1/labor-qa/ask" | python3 -m json.tool

echo ""
echo "== 2. 근거 없는 질문 (자료에 없으면 LLM을 부르지 않고 즉시 '자료 없음') =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"오늘 서울 날씨 어때?"}' \
  "$BASE_URL/api/v1/labor-qa/ask" | python3 -m json.tool

echo ""
echo "== 3. 대화 세션 생성 =="
SESSION_ID=$(curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/labor-qa/sessions" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "sessionId=$SESSION_ID"

echo ""
echo "== 4. 세션 안에서 질문 =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"연장근로 수당은 얼마인가요?"}' \
  "$BASE_URL/api/v1/labor-qa/sessions/$SESSION_ID/ask" | python3 -m json.tool

echo ""
echo "== 5. 후속 질문 (앞 질문 맥락을 이어받는지 확인) =="
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"그럼 5인 미만은요?"}' \
  "$BASE_URL/api/v1/labor-qa/sessions/$SESSION_ID/ask" | python3 -m json.tool

echo ""
echo "== 6. 대화 상세 (질문·답변 이력이 쌓였는지) =="
curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/api/v1/labor-qa/sessions/$SESSION_ID" | python3 -m json.tool

echo ""
echo "== 7. 적재 결과를 DB에서 직접 확인하려면 =="
echo "   PGPASSWORD=\$VECTOR_DB_PASSWORD psql -h 127.0.0.1 -U albam_app -d albam_vector \\"
echo "     -c \"SELECT count(*) AS 청크수, min(id) AS 샘플ID FROM vector_store;\""
