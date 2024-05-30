import scala.jdk.CollectionConverters.MapHasAsJava
import software.amazon.awscdk.Duration
import software.amazon.awscdk.services.cloudwatch.Alarm
import software.amazon.awscdk.services.cloudwatch.ComparisonOperator
import software.amazon.awscdk.services.cloudwatch.CreateAlarmOptions
import software.constructs.Construct

abstract class MetricNamespace:
  ns =>
  val namespace: String
  abstract class MetricDimension:
    val dimensionsMap: Map[String, String]

    final def metric(name: String)(statistic: MetricStatistic) = Metric(
      namespace = ns.namespace,
      dimensionsMap = this.dimensionsMap,
      metricName = name,
      statistic = statistic,
    )
object MetricNamespace:
  object CWAgent extends MetricNamespace:
    override val namespace: String = "CWAgent"
    final class InstanceDimension(id: String) extends MetricDimension:
      override val dimensionsMap: Map[String, String] = Map("InstanceId" -> id)

      inline def memUsedPercent = this.metric("mem_used_percent")
      inline def swapUsedPercent = this.metric("swap_used_percent")

      inline def detailed(imageId: String, instanceType: String) = DetailedInstanceDimension(
        instanceId = id,
        imageId = imageId,
        instanceType = instanceType,
      )

    final class DetailedInstanceDimension(instanceId: String, imageId: String, instanceType: String):
      inline def mountPoint(device: String, fstype: String, path: String) = MountPointDimension(
        instanceId = instanceId,
        imageId = imageId,
        instanceType = instanceType,
        device = device,
        fstype = fstype,
        path = path,
      )

    final class MountPointDimension(
        instanceId: String,
        imageId: String,
        instanceType: String,
        device: String,
        fstype: String,
        path: String,
    ) extends MetricDimension:
      override val dimensionsMap: Map[String, String] = Map(
        "InstanceId" -> instanceId,
        "ImageId" -> imageId,
        "InstanceType" -> instanceType,
        "device" -> device,
        "fstype" -> fstype,
        "path" -> path,
      )

      inline def diskUsedPercent = this.metric("disk_used_percent")

    inline def instance(id: String) = InstanceDimension(id)
  object AWSEC2 extends MetricNamespace:
    override val namespace: String = "AWS/EC2"

    final class InstanceDimension(id: String) extends MetricDimension:
      override val dimensionsMap: Map[String, String] = Map("InstanceId" -> id)

      inline def cpuCreditBalance = this.metric("CPUCreditBalance")

    inline def instance(id: String) = InstanceDimension(id)

enum MetricStatistic:
  case Avg(val period: Duration)
  case Max(val period: Duration)
extension (builder: software.amazon.awscdk.services.cloudwatch.Metric.Builder)
  def periodWithStatistic(s: MetricStatistic) = s match
    case MetricStatistic.Avg(in) => builder.period(in).statistic("avg")
    case MetricStatistic.Max(in) => builder.period(in).statistic("max")

enum AlarmCondition:
  case GreaterThan(val threshold: Int)
  case LessThan(val threshold: Int)
extension (builder: CreateAlarmOptions.Builder)
  def condition(c: AlarmCondition) = c match
    case AlarmCondition.GreaterThan(threshold) =>
      builder.comparisonOperator(ComparisonOperator.GREATER_THAN_THRESHOLD).threshold(threshold)
    case AlarmCondition.LessThan(threshold) =>
      builder.comparisonOperator(ComparisonOperator.LESS_THAN_THRESHOLD).threshold(threshold)

final class Metric(
    val namespace: String,
    val dimensionsMap: Map[String, String] = Map(),
    val metricName: String,
    val statistic: MetricStatistic,
):
  val builder = software.amazon.awscdk.services.cloudwatch.Metric.Builder.create()
  builder
    .namespace(namespace)
    .dimensionsMap(dimensionsMap.asJava)
    .metricName(metricName)
    .periodWithStatistic(statistic)
  val obj = builder.build()

  inline def alarmOn(
      condition: AlarmCondition
  )(id: String, evaluationPeriods: Int = 1, displayName: Option[String] = None)(using Construct) =
    this.createAlarm(id, condition, evaluationPeriods, displayName)

  def createAlarm(
      id: String,
      condition: AlarmCondition,
      evaluationPeriods: Int = 1,
      alarmName: Option[String] = None,
  )(using scope: Construct): Alarm =
    val optionsBuilder = CreateAlarmOptions
      .builder()
      .condition(condition)
      .evaluationPeriods(evaluationPeriods)
    for x <- alarmName do optionsBuilder.alarmName(x)
    this.obj.createAlarm(scope, id, optionsBuilder.build())
