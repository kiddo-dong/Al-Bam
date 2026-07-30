#!/bin/bash
# 엑셀/CSV 원가표 AI 마이그레이션 테스트
# 사용법: ./menu-import.sh {accessToken} {storeId} {파일경로.xlsx|.csv}
BASE_URL="http://localhost:8080"
TOKEN="$1"
STORE_ID="$2"
FILE="$3"

echo "== 1. 원가표 업로드 -> AI 재료 추출 (미리보기, 저장 안 됨) =="
curl -s -H "Authorization: Bearer $TOKEN" \
  -F "file=@$FILE" \
  "$BASE_URL/api/v1/stores/$STORE_ID/menu-import/ingredients/analyze" | python3 -m json.tool

echo ""
echo "== 2. 확인한 목록 실제 등록 (accepted 목록을 items로 넣어 호출) =="
echo "curl -s -X POST -H 'Authorization: Bearer \$TOKEN' -H 'Content-Type: application/json' \\"
echo "  -d '{\"items\":[{\"name\":\"원두\",\"productInfo\":null,\"price\":15000,\"packageQty\":1000,\"unit\":\"G\",\"lossRate\":0,\"category\":\"커피\"}]}' \\"
echo "  $BASE_URL/api/v1/stores/$STORE_ID/menu-import/ingredients/confirm"
