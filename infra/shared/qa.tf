# QA 콘솔 (safori-team/safori-qa). 상시 인스턴스 없이 Lambda 1개 + S3 버킷 1개로 운영한다.
# 코드는 safori-qa 의 deploy.yml 이 배포하고, 비밀번호는 README 절차로 넣는다.
# 코드/환경변수 변경은 테라폼이 되돌리지 않도록 ignore_changes 처리.
resource "aws_s3_bucket" "qa" {
  bucket = "safori-qa-${local.account_id}"
}

resource "aws_s3_bucket_public_access_block" "qa" {
  bucket = aws_s3_bucket.qa.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "qa" {
  bucket = aws_s3_bucket.qa.id

  rule {
    id     = "expire-screenshots"
    status = "Enabled"

    filter {
      prefix = "screenshots/"
    }

    expiration {
      days = 30
    }
  }
}

data "archive_file" "qa_placeholder" {
  type        = "zip"
  output_path = "${path.module}/.build/safori-qa.zip"

  source {
    filename = "lambda/handler.js"
    content  = <<-EOT
      exports.handler = async () => {
        throw new Error("placeholder: deploy the real code from safori-qa");
      };
    EOT
  }
}

data "aws_iam_policy_document" "qa_lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "qa_lambda" {
  name               = "safori-qa-lambda"
  assume_role_policy = data.aws_iam_policy_document.qa_lambda_assume.json
}

resource "aws_iam_role_policy_attachment" "qa_lambda_logs" {
  role       = aws_iam_role.qa_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

data "aws_iam_policy_document" "qa_storage" {
  statement {
    sid       = "ReadWriteObjects"
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.qa.arn}/*"]
  }

  # 없는 키를 읽을 때 403 대신 404 를 받으려면 필요하다
  statement {
    sid       = "ListBucket"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.qa.arn]
  }
}

resource "aws_iam_role_policy" "qa_lambda" {
  name   = "safori-qa-lambda"
  role   = aws_iam_role.qa_lambda.id
  policy = data.aws_iam_policy_document.qa_storage.json
}

resource "aws_lambda_function" "qa" {
  function_name    = "safori-qa"
  role             = aws_iam_role.qa_lambda.arn
  runtime          = "nodejs22.x"
  handler          = "lambda/handler.handler"
  timeout          = 15
  memory_size      = 256
  filename         = data.archive_file.qa_placeholder.output_path
  source_code_hash = data.archive_file.qa_placeholder.output_base64sha256

  environment {
    variables = {
      QA_BUCKET = aws_s3_bucket.qa.bucket
    }
  }

  lifecycle {
    ignore_changes = [filename, source_code_hash, environment]
  }
}

# 인증은 Lambda 가 비밀번호로 직접 한다
resource "aws_lambda_function_url" "qa" {
  function_name      = aws_lambda_function.qa.function_name
  authorization_type = "NONE"
}

resource "aws_lambda_permission" "qa_url" {
  statement_id           = "FunctionUrlPublic"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.qa.function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}

resource "aws_lambda_permission" "qa_url_invoke" {
  statement_id             = "FunctionUrlInvoke"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.qa.function_name
  principal                = "*"
  invoked_via_function_url = true
}

# safori-qa 는 public 레포다. main 브랜치 실행만 role 을 쓸 수 있다.
data "aws_iam_policy_document" "qa_ci_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      # 새 레포는 이름 대신 고정 ID 가 들어간 sub 를 쓴다 (레포를 지우고 같은 이름으로 다시 만들어도 이 role 을 못 쓴다)
      values = ["repo:safori-team@183119563/safori-qa@1441451233:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "qa_ci" {
  name               = "safori-qa-ci"
  assume_role_policy = data.aws_iam_policy_document.qa_ci_assume.json
}

resource "aws_iam_role_policy" "qa_ci_storage" {
  name   = "storage"
  role   = aws_iam_role.qa_ci.id
  policy = data.aws_iam_policy_document.qa_storage.json
}

data "aws_iam_policy_document" "qa_ci_deploy" {
  statement {
    sid       = "DeployCode"
    actions   = ["lambda:UpdateFunctionCode"]
    resources = [aws_lambda_function.qa.arn]
  }
}

resource "aws_iam_role_policy" "qa_ci_deploy" {
  name   = "deploy"
  role   = aws_iam_role.qa_ci.id
  policy = data.aws_iam_policy_document.qa_ci_deploy.json
}
