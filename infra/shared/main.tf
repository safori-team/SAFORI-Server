# 계정 공용 리소스. 환경(alpha/prod) 스택보다 먼저 apply 한다.
#   network.tf  VPC / subnet — alpha·prod 가 같이 쓴다
#   rds.tf      MySQL 1대 — alpha·prod 는 스키마(safori_alpha / safori_prod)와 DB 사용자로 분리
#   ecr.tf      이미지 리포지토리
#   docs.tf     API 문서 버킷
#   github.tf   GitHub Actions OIDC
# 환경 스택은 여기 리소스를 이름/태그로 찾는다(data source).

terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # bucket 은 -backend-config 로 주입 (계정 ID 가 들어가므로 public 레포에 두지 않는다)
  backend "s3" {
    key          = "shared/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "safori"
      Stack     = "shared"
      ManagedBy = "terraform"
    }
  }
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

data "aws_caller_identity" "current" {}

locals {
  name       = "safori"
  account_id = data.aws_caller_identity.current.account_id
}
