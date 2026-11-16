# 음성 업로드(Presigned URL) + TTS 캐시 버킷, 감정 분석 Lambda 기록 버킷.
# 버킷 이름은 전역 유일해야 해서 계정 ID 를 붙인다. 기본 암호화(SSE-S3)/BucketOwnerEnforced 는 S3 기본값.

resource "aws_s3_bucket" "voice" {
  bucket = "${local.name}-voice-${local.account_id}"
}

resource "aws_s3_bucket_public_access_block" "voice" {
  bucket = aws_s3_bucket.voice.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket" "emotion" {
  bucket = "${local.name}-emotion-analysis-${local.account_id}"
}

resource "aws_s3_bucket_public_access_block" "emotion" {
  bucket = aws_s3_bucket.emotion.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}
