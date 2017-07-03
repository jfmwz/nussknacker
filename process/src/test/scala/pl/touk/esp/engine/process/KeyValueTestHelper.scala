package pl.touk.esp.engine.process

import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import pl.touk.esp.engine.api._
import pl.touk.esp.engine.api.deployment.test.TestData
import pl.touk.esp.engine.api.exception.ExceptionHandlerFactory
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, WithCategories}
import pl.touk.esp.engine.api.test.TestParsingUtils
import pl.touk.esp.engine.flink.util.exception.VerboselyLoggingExceptionHandler
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.kafka.{KafkaConfig, KafkaSourceFactory}
import pl.touk.esp.engine.util.LoggingListener
import pl.touk.esp.engine.flink.util.source.CsvSchema
import pl.touk.esp.engine.process.compiler.StandardFlinkProcessCompiler

import scala.concurrent._

object KeyValueTestHelper {

  case class KeyValue(key: String, value: Int, date: Date)

  object KeyValue {
    def apply(list: List[String]): KeyValue = {
      KeyValue(list.head, list(1).toInt, new Date(list(2).toLong))
    }
  }

  object processInvoker {

    def prepareCreator(exConfig: ExecutionConfig, data: List[KeyValue], kafkaConfig: KafkaConfig) = new ProcessConfigCreator {
      override def services(config: Config) = Map("mock" -> WithCategories(MockService))
      override def sourceFactories(config: Config) =
        Map(
          "kafka-keyvalue" -> WithCategories(new KafkaSourceFactory[KeyValue](
            kafkaConfig,
            new CsvSchema(KeyValue.apply),
            Some(new BoundedOutOfOrdernessTimestampExtractor[KeyValue](Time.minutes(10)) {
              override def extractTimestamp(element: KeyValue) = element.date.getTime
            }),
            TestParsingUtils.newLineSplit
          )
        ))
      override def sinkFactories(config: Config) = Map.empty
      override def listeners(config: Config) = Seq(LoggingListener)

      override def customStreamTransformers(config: Config) = Map()
      override def exceptionHandlerFactory(config: Config) = ExceptionHandlerFactory.noParams(VerboselyLoggingExceptionHandler(_))

      override def globalProcessVariables(config: Config) = Map.empty

      override def signals(config: Config) = Map()

      override def buildInfo(): Map[String, String] = Map.empty
    }

    def invokeWithKafka(process: EspProcess, config: KafkaConfig,
                        env: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment()) = {
      val creator = prepareCreator(env.getConfig, List.empty, config)
      val configuration = ConfigFactory.load()
      new StandardFlinkProcessCompiler(creator, configuration).createFlinkProcessRegistrar().register(env, process)
      MockService.data.clear()
      env.execute()
    }

  }

  object MockService extends Service {

    val data = new CopyOnWriteArrayList[Any]

    def invoke(@ParamName("input") input: Any)
              (implicit ec: ExecutionContext) =
      Future.successful(data.add(input))
  }

}