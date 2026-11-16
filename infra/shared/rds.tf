# MySQL 1대를 alpha·prod 가 같이 쓴다.
#   스키마: safori_alpha / safori_prod
#   사용자: safori_alpha / safori_prod — 각자 자기 스키마 권한만 (README "DB 스키마·사용자")
# 마스터 비밀번호는 Secrets Manager 가 관리한다(코드/state 에 비밀번호 없음). 앱은 마스터 계정을 쓰지 않는다.
# 인바운드 3306 규칙은 각 환경 스택이 자기 앱 SG 를 source 로 추가한다.
#
# MySQL 8.0 은 2026-07 표준 지원 종료(이후 Extended Support 과금) → 8.4 LTS.

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_allocated_storage" {
  type    = number
  default = 20
}

resource "aws_security_group" "db" {
  name        = "${local.name}-db"
  description = "RDS MySQL: ingress rules are added by env stacks"
  vpc_id      = aws_vpc.this.id

  tags = { Name = "${local.name}-db" }
}

resource "aws_db_subnet_group" "this" {
  name       = "${local.name}-db"
  subnet_ids = aws_subnet.db[*].id
}

resource "aws_db_instance" "this" {
  identifier     = "${local.name}-mysql"
  engine         = "mysql"
  engine_version = "8.4"
  instance_class = var.db_instance_class

  username                    = "admin"
  manage_master_user_password = true

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period    = 7
  deletion_protection        = true
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${local.name}-mysql-final"
}
