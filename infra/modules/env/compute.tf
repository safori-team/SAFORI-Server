# 앱 EC2. 컨테이너(앱/otel-collector)는 deploy.yml 이 SSM 으로 띄운다.
# 여기서는 Docker 설치, swap, env-repo clone, cloudflared(Tunnel) 까지.
# public IP 는 자동 할당(외부 API 호출용). Tunnel 이라 IP 가 바뀌어도 DNS 는 그대로다.

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_instance" "app" {
  count = var.instance_count

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

  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    region             = var.region
    app_dir            = var.app_dir
    docker_network     = var.docker_network
    deploy_key_param   = local.deploy_key_param
    env_repo_url       = var.env_repo_url
    tunnel_token_param = local.tunnel_token_param
  })

  # deploy.yml 의 SSM 타겟이 tag:Name=safori-<env> 이므로 prod 2대 모두 같은 Name
  tags = {
    Name  = local.name
    Index = tostring(count.index + 1)
  }

  # AMI 갱신·user-data 수정으로 인스턴스가 교체되지 않도록
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}
