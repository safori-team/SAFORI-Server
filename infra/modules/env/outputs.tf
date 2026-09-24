output "tunnel_token_parameter" {
  description = "Cloudflare Tunnel 토큰을 SecureString 으로 등록할 SSM 파라미터 이름"
  value       = local.tunnel_token_param
}

output "tunnel_service_url" {
  description = "Cloudflare 대시보드의 퍼블릭 호스트네임에 넣을 서비스 URL"
  value       = "http://localhost:${var.app_port}"
}

output "instance_ids" {
  value = aws_instance.app[*].id
}

output "db_schema" {
  description = "이 환경이 쓰는 스키마. 같은 이름의 DB 사용자를 만들어 이 스키마 권한만 준다"
  value       = local.db_schema
}

output "deploy_key_parameter" {
  description = "env-repo deploy key(비공개 키)를 SecureString 으로 등록할 SSM 파라미터 이름"
  value       = local.deploy_key_param
}

# GitHub Settings → Environments → <env> 에 넣을 값
output "github_environment" {
  value = {
    secrets = {
      AWS_ROLE_TO_ASSUME = aws_iam_role.deploy.arn
    }
    vars = {
      AWS_REGION       = var.region
      ECR_REPOSITORY   = var.ecr_repository_name
      SSM_TARGET_KEY   = "tag:Name"
      SSM_TARGET_VALUE = local.name
      APP_DIR          = var.app_dir
      CONTAINER_NAME   = "safori-server"
      APP_PORT         = tostring(var.app_port)
      MGMT_PORT        = tostring(var.mgmt_port)
      DOCKER_NETWORK   = var.docker_network
      DOCS_S3_BUCKET   = local.docs_bucket
    }
  }
}

# env-repo 의 <profile>.env 에서 바꿀 값 (DB 비밀번호는 README 절차에서 만든 값)
output "env_file_values" {
  value = {
    DB_URL                              = "jdbc:mysql://${data.aws_db_instance.main.address}:3306/${local.db_schema}"
    DB_USERNAME                         = local.db_schema
    AWS_REGION                          = var.region
    AWS_S3_BUCKET                       = aws_s3_bucket.voice.bucket
    AWS_ACCESS_KEY                      = "(비움 — instance role 사용)"
    AWS_SECRET_KEY                      = "(비움 — instance role 사용)"
    EMOTION_ANALYSIS_REQUEST_QUEUE_URL  = aws_sqs_queue.request.url
    EMOTION_ANALYSIS_RESPONSE_QUEUE_URL = aws_sqs_queue.response.url
  }
}
