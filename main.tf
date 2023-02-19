provider "aws" {
  region = "ap-northeast-1"
}

locals {
  function_name = "CrescentServerAlarmReceiver"
}

data "aws_sns_topic" "memory_pressure_alarm" {
  name = "CrescentHighMemoryPressureAlarm"
}

resource "aws_sqs_queue" "memory_pressure_alarm" {
  name = "CrescentHighMemoryPressureAlarm"
}

resource "aws_sns_topic_subscription" "memory_pressure_alarm_queueing" {
  topic_arn = data.aws_sns_topic.memory_pressure_alarm.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.memory_pressure_alarm.arn
}

resource "aws_lambda_function" "alarm_receiver" {
  function_name = local.function_name
  role          = aws_iam_role.execution_role.arn

  filename         = "${path.module}/target/lambda/crescent-alarmgrid/bootstrap.zip"
  source_code_hash = filebase64sha256("${path.module}/target/lambda/crescent-alarmgrid/bootstrap.zip")
  handler          = "hello.handler"
  runtime          = "provided.al2"
}

resource "aws_cloudwatch_log_group" "alarm_receiver" {
  name              = "/aws/lambda/${local.function_name}"
  retention_in_days = 1
}

resource "aws_lambda_event_source_mapping" "invoke_from_queue" {
  function_name    = local.function_name
  event_source_arn = aws_sqs_queue.memory_pressure_alarm.arn
}
