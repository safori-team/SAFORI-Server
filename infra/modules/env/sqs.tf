# 소분류 감정 분석 파이프라인: 앱 → request → Lambda → response → 앱
# 환경마다 따로 둔다. 큐를 공유하면 alpha/prod 가 서로의 응답을 가로챈다.
# (최대 메시지 크기는 AWS 기본값 1MiB — 옛 계정과 동일)

# 옛 계정엔 DLQ 가 없었다. 분석 실패 메시지가 보존 기간(4일) 내내 재시도되며
# Gemini 를 호출하지 않도록 3회 실패 후 DLQ 로 뺀다.
resource "aws_sqs_queue" "request_dlq" {
  name                      = "${local.name}-emotion-request-dlq"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}

resource "aws_sqs_queue" "request" {
  name                       = "${local.name}-emotion-request"
  visibility_timeout_seconds = 900
  message_retention_seconds  = 345600
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.request_dlq.arn
    maxReceiveCount     = 3
  })
}

resource "aws_sqs_queue" "response" {
  name                       = "${local.name}-emotion-response"
  visibility_timeout_seconds = 60
  message_retention_seconds  = 345600
  receive_wait_time_seconds  = 20
  sqs_managed_sse_enabled    = true
}
