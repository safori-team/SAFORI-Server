# 서버 이미지는 alpha/prod 가 같은 리포지토리를 쓴다(태그 = 커밋 SHA).
resource "aws_ecr_repository" "server" {
  name                 = "safori-server"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# 옛 계정은 lifecycle 없이 이미지가 104개 쌓였다.
resource "aws_ecr_lifecycle_policy" "server" {
  repository = aws_ecr_repository.server.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 50개만 유지"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 50
      }
      action = { type = "expire" }
    }]
  })
}

# 감정 분석 Lambda 컨테이너 이미지
resource "aws_ecr_repository" "emotion" {
  name                 = "safori-emotion-native"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository_policy" "emotion_lambda_pull" {
  repository = aws_ecr_repository.emotion.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "LambdaECRImageRetrievalPolicy"
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = ["ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"]
      Condition = {
        StringLike = {
          "aws:sourceArn" = "arn:aws:lambda:${var.region}:${local.account_id}:function:*"
        }
      }
    }]
  })
}
