# EC2 instance role + GitHub Actions 배포 role.
# 옛 계정은 앱이 IAM user(safori_server)의 고정 access key 로 SQS/S3 에 접근했다.
# 여기서는 instance role 로 대체한다 → .env 의 AWS_ACCESS_KEY/AWS_SECRET_KEY 는 비운다.
# (IMDS hop limit 2 라 컨테이너 안에서도 instance role 자격증명을 받는다)

# ── EC2 instance role ───────────────────────────────────────────────────────
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${local.name}-app"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "app_ssm_core" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "app" {
  statement {
    sid       = "EcrAuthToken"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [data.aws_ecr_repository.server.arn]
  }

  statement {
    sid       = "SendEmotionAnalysisRequest"
    actions   = ["sqs:SendMessage", "sqs:GetQueueUrl", "sqs:GetQueueAttributes"]
    resources = [aws_sqs_queue.request.arn]
  }

  statement {
    sid = "ConsumeEmotionAnalysisResponse"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:ChangeMessageVisibility",
      "sqs:GetQueueAttributes",
      "sqs:GetQueueUrl",
    ]
    resources = [aws_sqs_queue.response.arn]
  }

  # 음성 업로드 Presigned URL 발급 + TTS 캐시
  statement {
    sid       = "VoiceBucketObjects"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.voice.arn}/*"]
  }

  statement {
    sid       = "VoiceBucketList"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.voice.arn]
  }

  # 부팅 시 env-repo deploy key, Tunnel 토큰 조회 (user-data)
  statement {
    sid     = "BootstrapSecrets"
    actions = ["ssm:GetParameter"]
    resources = [
      "arn:aws:ssm:${var.region}:${local.account_id}:parameter${local.deploy_key_param}",
      "arn:aws:ssm:${var.region}:${local.account_id}:parameter${local.tunnel_token_param}",
    ]
  }
}

resource "aws_iam_role_policy" "app" {
  name   = "${local.name}-app"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.app.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${local.name}-app"
  role = aws_iam_role.app.name
}

# ── GitHub Actions 배포 role (deploy.yml / docs.yml) ────────────────────────
data "aws_iam_policy_document" "deploy_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # GitHub Environment(alpha/prod) 로 실행된 잡만
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:environment:${var.env}"]
    }
  }
}

resource "aws_iam_role" "deploy" {
  name               = "${local.name}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.deploy_assume.json
}

data "aws_iam_policy_document" "deploy" {
  statement {
    sid       = "EcrAuthToken"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPushPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
    ]
    resources = [data.aws_ecr_repository.server.arn]
  }

  statement {
    sid       = "SSMSendDocument"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:${var.region}::document/AWS-RunShellScript"]
  }

  # 이 환경 인스턴스(Name=safori-<env>)에만 명령 전송
  statement {
    sid       = "SSMSendInstance"
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ec2:${var.region}:${local.account_id}:instance/*"]

    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/Name"
      values   = [local.name]
    }
  }

  statement {
    sid       = "SSMRead"
    actions   = ["ssm:ListCommands", "ssm:ListCommandInvocations", "ssm:GetCommandInvocation"]
    resources = ["*"]
  }

  statement {
    sid       = "DocsS3"
    actions   = ["s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
    resources = ["arn:aws:s3:::${local.docs_bucket}", "arn:aws:s3:::${local.docs_bucket}/*"]
  }
}

resource "aws_iam_role_policy" "deploy" {
  name   = "${local.name}-github-deploy"
  role   = aws_iam_role.deploy.id
  policy = data.aws_iam_policy_document.deploy.json
}
