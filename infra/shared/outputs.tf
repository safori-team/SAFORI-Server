output "rds_endpoint" {
  value = aws_db_instance.this.address
}

output "rds_master_secret_arn" {
  description = "마스터 비밀번호 (Secrets Manager). 스키마·사용자 생성에만 쓴다"
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "ecr_server_url" {
  value = aws_ecr_repository.server.repository_url
}

output "ecr_emotion_url" {
  value = aws_ecr_repository.emotion.repository_url
}

output "docs_bucket" {
  value = aws_s3_bucket.docs.bucket
}

output "docs_website_endpoint" {
  value = aws_s3_bucket_website_configuration.docs.website_endpoint
}
