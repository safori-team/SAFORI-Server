# 감정 분석 Lambda 껍데기. 코드 배포는 Lambda 레포에서 따로 한다.
#   오케스트레이터(zip): request 큐 트리거 → 분석 함수 호출 → S3 기록 → response 큐
#   분석 함수(이미지, arm64): 이미지가 ECR 에 올라간 뒤 emotion_analysis_image_uri 를 넣고 apply
# 코드/이미지/환경변수 변경은 테라폼이 되돌리지 않도록 ignore_changes 처리.

# ── 오케스트레이터 ──────────────────────────────────────────────────────────
data "archive_file" "orchestrator_placeholder" {
  type        = "zip"
  output_path = "${path.module}/.build/${local.name}-emotion-orchestrator.zip"

  # 실코드 배포 전에 메시지를 삭제하지 않도록 항상 실패한다
  source {
    filename = "lambda_function.py"
    content  = <<-EOT
      def lambda_handler(event, context):
          raise NotImplementedError("placeholder: deploy the real orchestrator code")
    EOT
  }
}

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "orchestrator" {
  name               = "${local.name}-emotion-orchestrator"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

resource "aws_iam_role_policy_attachment" "orchestrator_logs" {
  role       = aws_iam_role.orchestrator.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "orchestrator" {
  statement {
    sid       = "ConsumeRequest"
    actions   = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.request.arn]
  }

  statement {
    sid       = "SendResponse"
    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.response.arn]
  }

  statement {
    sid       = "InvokeAnalysis"
    actions   = ["lambda:InvokeFunction"]
    resources = ["arn:aws:lambda:${var.region}:${local.account_id}:function:${local.analysis_function_name}"]
  }

  statement {
    sid       = "WriteAnalysisRecords"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.emotion.arn}/analysis/*"]
  }
}

resource "aws_iam_role_policy" "orchestrator" {
  name   = "${local.name}-emotion-orchestrator"
  role   = aws_iam_role.orchestrator.id
  policy = data.aws_iam_policy_document.orchestrator.json
}

resource "aws_lambda_function" "orchestrator" {
  function_name    = "${local.name}-emotion-orchestrator"
  role             = aws_iam_role.orchestrator.arn
  runtime          = "python3.12"
  handler          = "lambda_function.lambda_handler"
  filename         = data.archive_file.orchestrator_placeholder.output_path
  source_code_hash = data.archive_file.orchestrator_placeholder.output_base64sha256
  timeout          = 60
  memory_size      = 128

  environment {
    variables = {
      ANALYSIS_FUNCTION_NAME = local.analysis_function_name
      RESPONSE_QUEUE_URL     = aws_sqs_queue.response.url
      S3_BUCKET              = aws_s3_bucket.emotion.bucket
      S3_PREFIX              = "analysis/"
    }
  }

  lifecycle {
    ignore_changes = [filename, source_code_hash, runtime, handler, layers]
  }
}

# 옛 계정 설정: batch 1, 부분 실패 보고, 동시 실행 최대 2
resource "aws_lambda_event_source_mapping" "request" {
  event_source_arn        = aws_sqs_queue.request.arn
  function_name           = aws_lambda_function.orchestrator.arn
  batch_size              = 1
  enabled                 = var.emotion_pipeline_enabled
  function_response_types = ["ReportBatchItemFailures"]

  scaling_config {
    maximum_concurrency = 2
  }

  depends_on = [aws_iam_role_policy.orchestrator]
}

# ── 분석 함수 (컨테이너 이미지) ──────────────────────────────────────────────
resource "aws_iam_role" "analysis" {
  name               = "${local.name}-emotion-analysis"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

resource "aws_iam_role_policy_attachment" "analysis_logs" {
  role       = aws_iam_role.analysis.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# GEMINI_API_KEY 등 환경변수는 코드에 두지 않는다. 배포 쪽(콘솔/Lambda 레포)에서 설정.
resource "aws_lambda_function" "analysis" {
  count = var.emotion_analysis_image_uri == "" ? 0 : 1

  function_name = local.analysis_function_name
  role          = aws_iam_role.analysis.arn
  package_type  = "Image"
  image_uri     = var.emotion_analysis_image_uri
  architectures = ["arm64"]
  timeout       = 120
  memory_size   = 1024

  lifecycle {
    ignore_changes = [image_uri, environment]
  }
}
