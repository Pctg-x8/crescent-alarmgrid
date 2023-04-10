import io.ct2.cdkhelper.buildArn
import io.ct2.cdkhelper.synthesize
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

@main def main(): Unit = synthesize {
  AlarmStack()
}

final class MemoryPressureAlarmResources(id: String = "Crescent-Alarmgrid-MemoryPressureResources")(using
    scope: Construct
) extends NestedStack(scope, id):
  val topic = sns.Topic.Builder.create(this, "topic").displayName("CrescentHighMemoryPressureAlarm").build()
  val queue = sqs.Queue.Builder.create(this, "queue").queueName("CrescentHighMemoryPressureAlarm").build()
  for mailTo <- sys.env get "ALARMGRID_MEMPRESSURE_MAILTO" do topic addSubscription EmailSubscription(mailTo)
  topic addSubscription SqsSubscription(queue)

  val physicalMemoryAlarm = Metric(
    namespace = "CWAgent",
    dimensionsMap = Map("InstanceId" -> "i-0195ecc0d1e95f81e"),
    metricName = "mem_used_percent",
    period = Duration minutes 5,
    statistic = "avg",
  )
    .createAlarm(
      "physicalMemoryAlarm",
      threshold = 85,
      comparisonOperator = ComparisonOperator.GREATER_THAN_THRESHOLD,
      evaluationPeriods = 1,
      alarmName = Some("CrescentServer-HighPhysicalMemoryPressure"),
    )
  val swapMemoryAlarm = Metric(
    namespace = "CWAgent",
    dimensionsMap = Map("InstanceId" -> "i-0195ecc0d1e95f81e"),
    metricName = "swap_used_percent",
    period = Duration minutes 5,
    statistic = "avg",
  ).createAlarm(
    "swapMemoryAlarm",
    threshold = 80,
    comparisonOperator = ComparisonOperator.GREATER_THAN_THRESHOLD,
    evaluationPeriods = 1,
    alarmName = Some("CrescentServer-HighSwapMemoryPressure"),
  )
  val memoryAlarm = CompositeAlarm.Builder
    .create(this, "memoryAlarm")
    .alarmRule(AlarmRule.allOf(physicalMemoryAlarm, swapMemoryAlarm))
    .compositeAlarmName("CrescentServer-HighMemoryPressure")
    .build()
  memoryAlarm addAlarmAction SnsAction(topic)

  def bindReceiverFunction(fn: lambda.IFunction): Unit =
    fn addEventSource SqsEventSource(this.queue)
    this.queue grantConsumeMessages fn

final class AlarmStack(id: String = "Crescent-Alarmgrid")(using
    scope: Construct
) extends Stack(scope, id, null):
  given Stack = Stack of this
  given Construct = this

  val receiverFunction = lambda.Function.Builder
    .create(this, "function")
    .functionName("CrescentServerAlarmReceiver")
    .code(lambda.Code fromAsset "../target/lambda/crescent-alarmgrid/bootstrap.zip")
    .handler("hello.handler")
    .runtime(lambda.Runtime.PROVIDED_AL2)
    .logRetention(logs.RetentionDays.ONE_DAY)
    .build()

  MemoryPressureAlarmResources() bindReceiverFunction receiverFunction

final class Metric(
    val namespace: String,
    val dimensionsMap: Map[String, String] = Map(),
    val metricName: String,
    val period: Duration,
    val statistic: String,
):
  val builder = software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
  builder
    .namespace(namespace)
    .dimensionsMap(dimensionsMap.asJava)
    .metricName(metricName)
    .period(period)
    .statistic(statistic)
  val obj = builder.build()

  def createAlarm(
      id: String,
      threshold: Int,
      comparisonOperator: ComparisonOperator,
      evaluationPeriods: Int,
      alarmName: Option[String] = None,
  )(using scope: Construct): Alarm =
    val optionsBuilder = CreateAlarmOptions
      .builder()
      .threshold(threshold)
      .comparisonOperator(comparisonOperator)
      .evaluationPeriods(evaluationPeriods)
    for x <- alarmName do optionsBuilder.alarmName(x)
    this.obj.createAlarm(scope, id, optionsBuilder.build())
