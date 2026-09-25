# 환경 하나(alpha 또는 prod)의 스택: EC2, SQS, S3, Lambda 껍데기, 배포 role.
# 앞단은 Cloudflare Tunnel(환경마다 1개). EC2 의 cloudflared 가 Cloudflare 로 먼저 연결하므로
# 로드밸런서·EIP·인바운드 포트가 없다. 인스턴스를 늘리면 같은 Tunnel 에 replica 로 붙는다.
# VPC 와 RDS 는 shared 스택 것을 쓴다. DB 는 스키마 safori_<env> + 전용 사용자로 분리.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.4"
    }
  }
}

locals {
  name       = "safori-${var.env}"
  account_id = data.aws_caller_identity.current.account_id
  db_schema  = "safori_${var.env}"

  # shared 스택과 같은 규칙
  docs_bucket = "safori-api-docs-${local.account_id}"

  public_subnet_ids = sort(data.aws_subnets.public.ids)

  analysis_function_name = "${local.name}-emotion-analysis"
  deploy_key_param       = "/safori/${var.env}/env-repo-deploy-key"
  tunnel_token_param     = "/safori/${var.env}/cloudflared-token"

  # deploy.yml 이 배포 성공 시 기록 → ASG 가 띄운 인스턴스가 부팅 때 다시 띄운다
  release_image_param  = "/safori/${var.env}/deploy/image"
  release_script_param = "/safori/${var.env}/deploy/script"
  container_name       = "safori-server"
}

data "aws_caller_identity" "current" {}

# ── shared 스택 리소스 ──────────────────────────────────────────────────────
data "aws_vpc" "main" {
  tags = { Name = "safori-vpc" }
}

data "aws_subnets" "public" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.main.id]
  }

  tags = { Tier = "public" }
}

data "aws_security_group" "db" {
  name   = "safori-db"
  vpc_id = data.aws_vpc.main.id
}

data "aws_db_instance" "main" {
  db_instance_identifier = "safori-mysql"
}

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

data "aws_ecr_repository" "server" {
  name = var.ecr_repository_name
}
