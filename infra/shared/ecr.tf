# 서버 이미지는 alpha/prod 가 같은 리포지토리를 쓴다(태그 = <env>-<커밋 SHA>).
resource "aws_ecr_repository" "server" {
  name                 = "safori-server"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# 환경별로 최근 5개만 유지한다. 한 리포지토리를 같이 쓰므로 전체 개수로 자르면
# alpha 빌드가 쌓일 때 prod 가 쓰는 이미지까지 지워진다 → 태그 접두어별로 센다.
resource "aws_ecr_lifecycle_policy" "server" {
  repository = aws_ecr_repository.server.name

  policy = jsonencode({
    rules = [for i, env in ["alpha", "prod"] : {
      rulePriority = i + 1
      description  = "${env} 최근 5개만 유지"
      selection = {
        tagStatus     = "tagged"
        tagPrefixList = ["${env}-"]
        countType     = "imageCountMoreThan"
        countNumber   = 5
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
