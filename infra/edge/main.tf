# Cloudflare 엣지: API 호스트 앞의 파킹(점검) Worker. 환경(alpha/prod)마다 Worker·KV·라우트·Cron 1벌.
# 서버를 꺼도 Worker 가 503 + 사유를 주고, 사유는 KV 에 있으므로 서버 없이 등록·해제한다.
# 코드: worker/parking.js (테스트: cd worker && npm test)
#
# Tunnel·퍼블릭 호스트네임은 여전히 대시보드에서 관리한다(README "Cloudflare Tunnel").
# 헬스 체크를 위해 Tunnel 에 /actuator/health → http://safori-app:9082 규칙이 하나 필요하다(README).

terraform {
  required_version = ">= 1.10"

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.0"
    }
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    key          = "edge/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

# 토큰은 CLOUDFLARE_API_TOKEN 환경변수로. 권한: Account › Workers Scripts·Workers KV Storage Edit, Zone › Workers Routes Edit
provider "cloudflare" {}

provider "aws" {
  region = "ap-northeast-2"

  default_tags {
    tags = {
      Project   = "safori"
      Stack     = "edge"
      ManagedBy = "terraform"
    }
  }
}

variable "cloudflare_account_id" {
  description = "public 레포라 커밋하지 않는다 — TF_VAR_cloudflare_account_id 로 주입"
  type        = string
}

variable "cloudflare_zone_id" {
  description = "API 도메인의 zone. TF_VAR_cloudflare_zone_id 로 주입"
  type        = string
}

variable "environments" {
  description = <<-EOT
    환경별 API 호스트와 웹 출처. 키는 alpha / prod.
    hostname: Tunnel 퍼블릭 호스트네임과 같은 값 (예: api.example.com)
    allowed_origins: 브라우저가 파킹 응답 본문을 읽을 수 있게 CORS 를 허용할 웹 출처. 같은 출처면 비워 둔다.
  EOT
  type = map(object({
    hostname            = string
    allowed_origins     = optional(list(string), [])
    backoffice_hostname = optional(string)
  }))
}

variable "health_cron" {
  description = "자동 파킹 상태 확인 주기. 상태가 바뀔 때만 KV 에 쓰므로 1분이어도 무료 한도 안"
  type        = string
  default     = "* * * * *"
}

# 운영자 키는 SSM 에 SecureString 으로 먼저 넣는다(README). 서버의 operator.api-key 와 다른 값.
data "aws_ssm_parameter" "operator_key" {
  for_each = var.environments

  name            = "/safori/${each.key}/parking-operator-key"
  with_decryption = true
}

locals {
  # 원본이 꺼져 있어도 첫 방문 HTML 안에서 바로 그림을 표시한다.
  worker_source = replace(
    file("${path.module}/worker/parking.js"),
    "__PARKING_SCENE_BASE64__",
    filebase64("${path.module}/worker/parking-scene.webp"),
  )
}

resource "cloudflare_workers_kv_namespace" "parking" {
  for_each = var.environments

  account_id = var.cloudflare_account_id
  title      = "safori-${each.key}-parking"
}

resource "cloudflare_workers_script" "parking" {
  for_each = var.environments

  account_id         = var.cloudflare_account_id
  script_name        = "safori-${each.key}-parking"
  content            = local.worker_source
  main_module        = "parking.js"
  compatibility_date = "2026-11-01"

  bindings = [
    {
      name         = "PARKING"
      type         = "kv_namespace"
      namespace_id = cloudflare_workers_kv_namespace.parking[each.key].id
    },
    {
      name = "BACKOFFICE_HOSTNAME"
      type = "plain_text"
      text = each.value.backoffice_hostname == null ? "" : each.value.backoffice_hostname
    },
    {
      name = "HEALTH_URL"
      type = "plain_text"
      text = "https://${each.value.hostname}/actuator/health"
    },
    {
      name = "ALLOWED_ORIGINS"
      type = "plain_text"
      text = join(",", each.value.allowed_origins)
    },
    {
      name = "OPERATOR_KEY"
      type = "secret_text"
      text = data.aws_ssm_parameter.operator_key[each.key].value
    },
  ]
}

# 호스트 전체를 받는다. Worker 요청 한도(무료 하루 10만)를 넘으면 대시보드의 라우트 "실패 모드"가
# fail open 이어야 Worker 를 건너뛰고 원본으로 간다(README).
resource "cloudflare_workers_route" "parking" {
  for_each = var.environments

  zone_id = var.cloudflare_zone_id
  pattern = "${each.value.hostname}/*"
  script  = cloudflare_workers_script.parking[each.key].script_name
}

# Cloudflare Access checks the console hostname before routing to this Worker.
resource "cloudflare_workers_route" "backoffice" {
  for_each = { for name, env in var.environments : name => env if env.backoffice_hostname != null }

  zone_id = var.cloudflare_zone_id
  pattern = "${each.value.backoffice_hostname}/*"
  script  = cloudflare_workers_script.parking[each.key].script_name
}

resource "cloudflare_workers_cron_trigger" "health" {
  for_each = var.environments

  account_id  = var.cloudflare_account_id
  script_name = cloudflare_workers_script.parking[each.key].script_name
  schedules   = [{ cron = var.health_cron }]
}

output "parking" {
  value = {
    for env, cfg in var.environments : env => {
      worker       = cloudflare_workers_script.parking[env].script_name
      kv_namespace = cloudflare_workers_kv_namespace.parking[env].id
      status_url   = "https://${cfg.hostname}/v1/api/parking"
    }
  }
}
