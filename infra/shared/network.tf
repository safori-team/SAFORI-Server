# alpha·prod 공용 VPC. 환경 분리는 SG/Tunnel/IAM/DB 사용자 단위로 한다.
# public subnet: EC2 (인바운드 없음, 외부 API 호출용 public IP) / db subnet: RDS
# NAT 없음: EC2 는 public IP 로 외부 API(Gemini/OpenAI/Sentry/FCM)에 나간다.
# 환경 스택은 태그 Name=safori-vpc, Tier=public|db 로 찾는다.

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "azs" {
  description = "t3a 가 모든 AZ 에 있지 않으므로 a/c"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.0.0/20", "10.0.16.0/20"]
}

variable "db_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.32.0/25", "10.0.33.0/25"]
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${local.name}-vpc" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${local.name}-igw" }
}

resource "aws_subnet" "public" {
  count = length(var.azs)

  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name}-public-${element(split("-", var.azs[count.index]), 2)}"
    Tier = "public"
  }
}

resource "aws_subnet" "db" {
  count = length(var.azs)

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.db_subnet_cidrs[count.index]
  availability_zone = var.azs[count.index]

  tags = {
    Name = "${local.name}-db-${element(split("-", var.azs[count.index]), 2)}"
    Tier = "db"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = { Name = "${local.name}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count = length(var.azs)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# VPC 내부 통신만 (local route 는 자동)
resource "aws_route_table" "db" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${local.name}-db-rt" }
}

resource "aws_route_table_association" "db" {
  count = length(var.azs)

  subnet_id      = aws_subnet.db[count.index].id
  route_table_id = aws_route_table.db.id
}

# S3 트래픽을 IGW 대신 게이트웨이 엔드포인트로 (무료)
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id, aws_route_table.db.id]

  tags = { Name = "${local.name}-s3-endpoint" }
}
