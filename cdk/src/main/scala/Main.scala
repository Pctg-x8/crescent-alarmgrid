import io.ct2.cdkhelper.buildArn
import io.ct2.cdkhelper.synthesize
import scala.annotation.threadUnsafe
import scala.jdk.CollectionConverters.given
import software.amazon.awscdk.ArnFormat
import software.amazon.awscdk.Duration
import software.amazon.awscdk.NestedStack
import software.amazon.awscdk.Stack
import software.amazon.awscdk.StackProps
import software.amazon.awscdk.services.cloudwatch.Alarm
import software.amazon.awscdk.services.cloudwatch.AlarmProps
import software.amazon.awscdk.services.cloudwatch.AlarmRule
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator
import software.amazon.awscdk.services.cloudwatch.CompositeAlarm
import software.amazon.awscdk.services.cloudwatch.CompositeAlarmProps
import software.amazon.awscdk.services.cloudwatch.CreateAlarmOptions
import software.amazon.awscdk.services.cloudwatch.Metric
import software.amazon.awscdk.services.cloudwatch.MetricOptions
import software.amazon.awscdk.services.cloudwatch.MetricProps
import software.amazon.awscdk.services.cloudwatch.Statistic
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction
import software.amazon.awscdk.services.iam.ServicePrincipal
import software.amazon.awscdk.services.lambda
import software.amazon.awscdk.services.lambda.EventSourceMapping
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource
import software.amazon.awscdk.services.logs
import software.amazon.awscdk.services.sns
import software.amazon.awscdk.services.sns.subscriptions.EmailSubscription
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription
import software.amazon.awscdk.services.sqs
import software.constructs.Construct

final val ServerInstanceID = "i-0195ecc0d1e95f81e"
final val ServerImageID = "ami-0dee43a4abd99c264"
final val ServerInstanceType = "t4g.small"

@main def main(): Unit = synthesize {
  AlarmStack()
}

final class AlarmStack(id: String = "Crescent-Alarmgrid")(using
    scope: Construct
) extends Stack(scope, id, null):
  given Stack = Stack of this
  given Construct = this

  val queue = sqs.Queue.Builder.create(this, "queue").queueName("Crescent-Alarmgrid").build()
  val receiverFunction = lambda.Function.Builder
    .create(this, "function")
    .functionName("CrescentServerAlarmReceiver")
    .code(lambda.Code fromAsset "../target/lambda/crescent-alarmgrid/bootstrap.zip")
    .handler("hello.handler")
    .runtime(lambda.Runtime.PROVIDED_AL2)
    .build()
  receiverFunction addEventSource SqsEventSource(queue)
  queue grantConsumeMessages receiverFunction

  val receiverFunctionLogGroup = logs.LogGroup.Builder
    .create(this, "functionLogGroup")
    .retention(logs.RetentionDays.ONE_DAY)
    .logGroupName(s"/aws/lambda/${receiverFunction.getFunctionName()}")
    .build()

  MemoryPressureAlarmResources() subscribeTo queue
  HighStorageUsageAlarmResources() subscribeTo queue
  LowCPUCreditBalanceAlarmResources() subscribeTo queue

final class MemoryPressureAlarmResources(id: String = "Crescent-Alarmgrid-MemoryPressureResources")(using
    scope: Construct
) extends NestedStack(scope, id):
  val topic =
    sns.Topic.Builder.create(this, "memoryPressure-topic").displayName("Crescent-HighMemoryPressureAlarm").build()
  for mailTo <- sys.env get "ALARMGRID_MAILTO" do topic addSubscription EmailSubscription(mailTo)

  val physicalMemoryAlarm = MetricNamespace.CWAgent
    .instance(ServerInstanceID)
    .memUsedPercent(MetricStatistic.Avg(Duration minutes 5))
    .alarmOn(AlarmCondition.GreaterThan(85))(
      "memoryPressure-physicalMemoryAlarm",
      displayName = Some("CrescentServer-HighPhysicalMemoryPressure"),
    )
  val swapMemoryAlarm = MetricNamespace.CWAgent
    .instance(ServerInstanceID)
    .swapUsedPercent(MetricStatistic.Avg(Duration minutes 5))
    .alarmOn(AlarmCondition.GreaterThan(80))(
      "memoryPressure-swapMemoryAlarm",
      displayName = Some("CrescentServer-HighSwapMemoryPressure"),
    )
  val memoryAlarm = CompositeAlarm.Builder
    .create(this, "memoryPressure-alarm")
    .alarmRule(AlarmRule.allOf(physicalMemoryAlarm, swapMemoryAlarm))
    .compositeAlarmName("CrescentServer-HighMemoryPressure")
    .build()
  memoryAlarm addAlarmAction SnsAction(topic)

  def subscribeTo(queue: sqs.Queue) = topic addSubscription SqsSubscription(queue)

final class HighStorageUsageAlarmResources(id: String = "Crescent-Alarmgrid-HighStorageUsageResources")(using
    scope: Construct
) extends NestedStack(scope, id):
  val topic =
    sns.Topic.Builder.create(this, "highStorageUsage-topic").displayName("Crescent-HighStorageUsageAlarm").build()
  for mailTo <- sys.env get "ALARMGRID_MAILTO" do topic addSubscription EmailSubscription(mailTo)

  val alarm = MetricNamespace.CWAgent
    .instance(ServerInstanceID)
    .detailed(ServerImageID, ServerInstanceType)
    .mountPoint("nvme0n1p1", "xfs", "/")
    .diskUsedPercent(MetricStatistic.Max(Duration minutes 5))
    .alarmOn(AlarmCondition.GreaterThan(85))(
      "highStorageUsage-alarm",
      displayName = Some("CrescentServer-HighStorageUsage"),
    )
  alarm addAlarmAction SnsAction(topic)

  def subscribeTo(queue: sqs.Queue) = topic addSubscription SqsSubscription(queue)

final class LowCPUCreditBalanceAlarmResources(id: String = "Crescent-Alarmgrid-LowCPUCreditBalanceResources")(using
    scope: Construct
) extends NestedStack(scope, id):
  val topic =
    sns.Topic.Builder.create(this, "lowCPUCreditBalance-topic").displayName("Crescent-LowCPUCreditBalanceAlarm").build()
  for mailTo <- sys.env get "ALARMGRID_MAILTO" do topic addSubscription EmailSubscription(mailTo)

  val alarm = MetricNamespace.AWSEC2
    .instance(ServerInstanceID)
    .cpuCreditBalance(MetricStatistic.Avg(Duration minutes 5))
    .alarmOn(AlarmCondition.LessThan(100))(
      "lowCPUCreditBalance-alarm",
      displayName = Some("CrescentServer-LowCPUCreditBalance"),
    )
  alarm addAlarmAction SnsAction(topic)

  def subscribeTo(queue: sqs.Queue) = topic addSubscription SqsSubscription(queue)
