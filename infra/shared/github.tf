# GitHub Actions OIDC. 환경별 배포 role 은 env 스택이 만든다.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}
