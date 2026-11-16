# prod: EC2 1대 + Cloudflare Tunnel(prod). DB 는 shared RDS 의 safori_prod 스키마.
#
# instance_count 를 늘리면 새 인스턴스가 같은 Tunnel 에 replica 로 붙는다(장애 시 넘김, 무료).
# 균등 분산·헬스체크가 필요하면 Cloudflare Load Balancing 을 Tunnel 앞에 둔다.
# ASG 로 관리하므로 인스턴스 ID 는 교체 때마다 바뀐다(배포는 tag:Name 으로 찾음).
# 늘리기 전에 앱/배포 쪽에서 필요한 것:
#   - @Scheduled 작업 중복 실행 방지 (ShedLock 등)

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

# 분석 Lambda 이미지. 비워 두면 apply 때 함수가 삭제되므로 digest 를 고정한다.
# URI 는 ECR 저장소 URL(계정 ID 포함)을 data source 로 읽어 조합한다 — 계정 ID 를 커밋하지 않기 위해.
# 이미지 교체는 Lambda 레포 배포가 하고 테라폼은 image_uri 를 되돌리지 않는다(ignore_changes).
# 이 값은 함수를 새로 만들 때만 쓰인다.
variable "emotion_analysis_image_digest" {
  type    = string
  default = "sha256:02c363026f869351170750dd9e95082ec473ccd82b9058965e471bae8559ac2b"
}

data "aws_ecr_repository" "emotion" {
  name = "safori-emotion-native"
}

module "safori" {
  source = "../../modules/env"

  env            = "prod"
  instance_count = 1

  # ASG 로 띄워 앱 헬스 실패 시 자동 교체. 출시 전에는 멈춰 두고(인스턴스 정지 가능), 출시 때 false 로 apply.
  auto_recovery           = true
  auto_recovery_suspended = true

  emotion_analysis_image_uri = "${data.aws_ecr_repository.emotion.repository_url}@${var.emotion_analysis_image_digest}"
}

output "safori" {
  value = module.safori
}
