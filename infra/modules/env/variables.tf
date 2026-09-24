variable "env" {
  description = "환경 이름 (alpha | prod). 리소스 이름, EC2 Name 태그(safori-<env>), DB 스키마(safori_<env>)에 쓰인다"
  type        = string

  validation {
    condition     = contains(["alpha", "prod"], var.env)
    error_message = "env 는 alpha 또는 prod."
  }
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

# ── EC2 ─────────────────────────────────────────────────────────────────────
variable "instance_count" {
  description = "public subnet(AZ)에 번갈아 배치. 모두 같은 Tunnel 에 replica 로 붙는다"
  type        = number
}

variable "instance_type" {
  description = "앱 3g + otel 192m + cloudflared 128m 를 띄우므로 메모리 4GB 이상"
  type        = string
  default     = "t3a.medium"
}

variable "root_volume_gb" {
  type    = number
  default = 30
}

variable "app_dir" {
  description = "deploy.yml 의 APP_DIR"
  type        = string
  default     = "/opt/safori"
}

variable "docker_network" {
  description = "deploy.yml 의 DOCKER_NETWORK"
  type        = string
  default     = "safori-net"
}

variable "app_port" {
  description = "호스트 포트(127.0.0.1) → 앱 컨테이너 8080 (deploy.yml 의 APP_PORT). Tunnel 서비스 URL 은 http://localhost:<app_port>"
  type        = number
  default     = 8080
}

variable "mgmt_port" {
  description = "호스트 포트 → 앱 actuator 9082 (deploy.yml 의 MGMT_PORT). 외부 비공개, 배포 헬스체크용"
  type        = number
  default     = 9082
}

variable "cloudflared_image" {
  description = "cloudflared 컨테이너 이미지. 특정 버전으로 고정하려면 태그를 바꾼다"
  type        = string
  default     = "cloudflare/cloudflared:latest"
}

variable "env_repo_url" {
  description = "Safori-Back-Env 레포 SSH URL"
  type        = string
  default     = "git@github.com:safori-team/Safori-Back-Env.git"
}

# ── 감정 분석 파이프라인 ─────────────────────────────────────────────────────
variable "emotion_pipeline_enabled" {
  description = "요청 큐 → 오케스트레이터 트리거 활성화. 실제 코드 배포 전에는 false"
  type        = bool
  default     = false
}

variable "emotion_analysis_image_uri" {
  description = "분석 Lambda 이미지 URI. 이미지 push 전에는 빈 값 → 함수 생성 안 함"
  type        = string
  default     = ""
}

# ── 공용 리소스 이름 (shared 스택) ───────────────────────────────────────────
variable "github_repo" {
  type    = string
  default = "safori-team/SAFORI-Server"
}

variable "ecr_repository_name" {
  type    = string
  default = "safori-server"
}
