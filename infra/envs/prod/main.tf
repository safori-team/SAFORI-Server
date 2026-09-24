# prod: EC2 1대 + Cloudflare Tunnel(prod). DB 는 shared RDS 의 safori_prod 스키마.
#
# instance_count 를 늘리면 새 인스턴스가 같은 Tunnel 에 replica 로 붙는다(장애 시 넘김, 무료).
# 균등 분산·헬스체크가 필요하면 Cloudflare Load Balancing 을 Tunnel 앞에 둔다.
# 늘리기 전에 앱/배포 쪽에서 필요한 것:
#   - @Scheduled 작업 중복 실행 방지 (ShedLock 등)
#   - deploy.yml 을 인스턴스별 순차 배포 + 전체 invocation 확인으로 수정

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

  backend "s3" {
    key          = "prod/terraform.tfstate"
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
      Env       = "prod"
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

  env            = "prod"
  instance_count = 1

  emotion_analysis_image_uri = var.emotion_analysis_image_uri
}

output "safori" {
  value = module.safori
}
