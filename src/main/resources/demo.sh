RESP=$(curl -s -X POST localhost:8080/api/v1/files \
  -H 'Content-Type: application/json' \
  -d '{"filename":"test.txt","contentType":"text/plain","sizeBytes":30,"burnAfterRead":true}')

UPLOAD_URL=$(echo "$RESP" | jq -r .uploadUrl)
CONTENT_TYPE=$(echo "$RESP" | jq -r .uploadContentType)
KEY=$(echo "$RESP" | jq -r .secretKey)
SHARE_URL=$(echo "$RESP" | jq -r .shareUrl)

curl -X PUT "$UPLOAD_URL" -H "Content-Type: $CONTENT_TYPE" --data-binary @/Users/ankitasingh/Downloads/test.txt
curl -s -X POST "localhost:8080/api/v1/files/$KEY/complete" | jq
echo "Open: $SHARE_URL"