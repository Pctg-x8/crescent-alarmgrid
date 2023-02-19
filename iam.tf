
resource "aws_iam_role" "execution_role" {
  name               = "${local.function_name}-ExecutionRole"
  path               = "/service-role/crescent/"
  assume_role_policy = data.aws_iam_policy_document.execution_assume_role_policy.json
}
data "aws_iam_policy_document" "execution_assume_role_policy" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}
resource "aws_iam_role_policy_attachment" "execution_logging_policy" {
  role       = aws_iam_role.execution_role.name
  policy_arn = aws_iam_policy.logging_policy.arn
}
resource "aws_iam_role_policy_attachment" "execution_queue_management_policy" {
  role       = aws_iam_role.execution_role.name
  policy_arn = aws_iam_policy.queue_management_policy.arn
}

resource "aws_iam_policy" "logging_policy" {
  name   = "${local.function_name}-Logging"
  path   = "/crescent/"
  policy = data.aws_iam_policy_document.logging_policy.json
}
data "aws_iam_policy_document" "logging_policy" {
  statement {
    effect    = "Allow"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.alarm_receiver.arn}:*"]
  }
}

resource "aws_iam_policy" "queue_management_policy" {
  name   = "${local.function_name}-QueueManagement"
  path   = "/crescent/"
  policy = data.aws_iam_policy_document.queue_management_policy.json
}
data "aws_iam_policy_document" "queue_management_policy" {
  statement {
    effect    = "Allow"
    actions   = ["sqs:DeleteMessage", "sqs:GetQueueAttributes", "sqs:ReceiveMessage"]
    resources = [aws_sqs_queue.memory_pressure_alarm.arn]
  }
}
