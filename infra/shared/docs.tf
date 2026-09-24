# API 문서 (docs.yml → S3 정적 웹사이트, docs/<branch>/ 경로)
resource "aws_s3_bucket" "docs" {
  bucket = "safori-api-docs-${local.account_id}"
}

resource "aws_s3_bucket_public_access_block" "docs" {
  bucket = aws_s3_bucket.docs.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_website_configuration" "docs" {
  bucket = aws_s3_bucket.docs.id

  index_document {
    suffix = "index.html"
  }
}

resource "aws_s3_bucket_policy" "docs" {
  bucket = aws_s3_bucket.docs.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicReadDocs"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.docs.arn}/docs/*"
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.docs]
}
