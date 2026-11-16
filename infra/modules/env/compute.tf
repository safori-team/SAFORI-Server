# 앱 EC2. 컨테이너(앱/otel-collector/cloudflared)는 배포 스크립트(remote-deploy.sh)가 띄운다.
# 여기서는 Docker 설치, swap, env-repo clone, Tunnel 토큰 준비까지.
# public IP 는 자동 할당(외부 API 호출용). Tunnel 이라 IP 가 바뀌어도 DNS 는 그대로다.
#
# auto_recovery = false : 일반 인스턴스 (alpha — 정지/시작해서 쓴다)
# auto_recovery = true  : ASG(최소·최대 = instance_count). 앱 헬스를 인스턴스가 스스로 ASG 에 보고해
#                         연속 실패하면 교체되고, 새 인스턴스는 부팅 때 마지막 성공 배포를 다시 띄운다.

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

locals {
  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    region               = var.region
    env                  = var.env
    app_dir              = var.app_dir
    docker_network       = var.docker_network
    container_name       = local.container_name
    deploy_key_param     = local.deploy_key_param
    env_repo_url         = var.env_repo_url
    tunnel_token_param   = local.tunnel_token_param
    auto_recovery        = var.auto_recovery
    health_script_b64    = filebase64("${path.module}/health-report.sh")
    release_image_param  = local.release_image_param
    release_script_param = local.release_script_param
  })
}

# ── 일반 인스턴스 ───────────────────────────────────────────────────────────
resource "aws_instance" "app" {
  count = var.auto_recovery ? 0 : var.instance_count

  ami                    = data.aws_ssm_parameter.al2023_ami.insecure_value
  instance_type          = var.instance_type
  subnet_id              = local.public_subnet_ids[count.index % length(local.public_subnet_ids)]
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
    encrypted   = true
  }

  user_data = local.user_data

  # deploy.yml 의 SSM 타겟이 tag:Name=safori-<env>
  tags = {
    Name  = local.name
    Index = tostring(count.index + 1)
  }

  # AMI 갱신·user-data 수정으로 인스턴스가 교체되지 않도록
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

# ── ASG (자동 복구) ─────────────────────────────────────────────────────────
resource "aws_launch_template" "app" {
  count = var.auto_recovery ? 1 : 0

  name                   = local.name
  image_id               = data.aws_ssm_parameter.al2023_ami.insecure_value
  instance_type          = var.instance_type
  vpc_security_group_ids = [aws_security_group.app.id]
  user_data              = base64encode(local.user_data)

  iam_instance_profile {
    name = aws_iam_instance_profile.app.name
  }

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  block_device_mappings {
    device_name = "/dev/xvda"

    ebs {
      volume_type = "gp3"
      volume_size = var.root_volume_gb
      encrypted   = true
    }
  }

  tag_specifications {
    resource_type = "instance"
    tags          = { Name = local.name }
  }
}

# 템플릿이 바뀌어도 기존 인스턴스는 그대로 두고, 다음 교체 때부터 새 버전이 쓰인다.
resource "aws_autoscaling_group" "app" {
  count = var.auto_recovery ? 1 : 0

  name                      = local.name
  min_size                  = var.instance_count
  max_size                  = var.instance_count
  desired_capacity          = var.instance_count
  vpc_zone_identifier       = local.public_subnet_ids
  health_check_type         = "EC2"
  health_check_grace_period = 900

  # 출시 전에는 헬스체크·교체를 멈춰 인스턴스를 정지해 둘 수 있게 한다
  suspended_processes = var.auto_recovery_suspended ? ["HealthCheck", "ReplaceUnhealthy", "AZRebalance"] : []

  launch_template {
    id      = aws_launch_template.app[0].id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = local.name
    propagate_at_launch = true
  }
}
