# alpha: EC2 1대 + Cloudflare Tunnel(alpha). DB 는 shared RDS 의 safori_alpha 스키마.
# 평소엔 인스턴스를 정지해 두고 확인할 때만 켠다. 켜면 cloudflared 가 다시 붙으므로 DNS 변경 불필요.

terraform {
  required_version = ">= 1.10"

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

  # bucket 은 -backend-config 로 주입
  backend "s3" {
    key          = "alpha/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = "ap-northeast-2"

  default_tags {
    tags = {
      Project   = "safori"
      Env       = "alpha"
      ManagedBy = "terraform"
    }
  }
}

variable "emotion_analysis_image_uri" {
  type    = string
  default = ""
}

module "safori" {
  source = "../../modules/env"

  env            = "alpha"
  instance_count = 1

  emotion_analysis_image_uri = var.emotion_analysis_image_uri
  emotion_pipeline_enabled   = true
}

output "safori" {
  value = module.safori
}
