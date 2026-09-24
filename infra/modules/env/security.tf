# 사용자 → Cloudflare ═ Tunnel ═ EC2 의 cloudflared(아웃바운드 연결) → 앱(127.0.0.1) → 공용 RDS(3306)
# Tunnel 은 EC2 가 먼저 바깥으로 연결하므로 인바운드는 하나도 열지 않는다.
# SSH 도 없다. 접속은 SSM Session Manager 로.
# (Terraform 은 SG 의 기본 egress-all 규칙을 지우므로 egress 를 명시한다)

resource "aws_security_group" "app" {
  name        = "${local.name}-app"
  description = "EC2 app: no inbound (Cloudflare Tunnel + SSM)"
  vpc_id      = data.aws_vpc.main.id

  tags = { Name = "${local.name}-app" }
}

# Cloudflare Tunnel, 외부 API, ECR/SSM, docker hub pull, RDS
resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

# ── 공용 RDS 에 이 환경 앱의 접근 허용 ──────────────────────────────────────
# 네트워크는 두 환경 모두 열리고, 스키마 접근은 DB 사용자 권한으로 막는다.
resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = data.aws_security_group.db.id
  description                  = "MySQL from ${local.name} app"
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
  referenced_security_group_id = aws_security_group.app.id
}
