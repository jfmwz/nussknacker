package pl.touk.nussknacker.engine.definition

import java.lang.reflect.{InvocationTargetException, Method}

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.MethodToInvoke
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, WithCategories}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor._
import pl.touk.nussknacker.engine.definition.MethodDefinitionExtractor.MethodDefinition
import pl.touk.nussknacker.engine.types.{EspTypeUtils, TypesInformationExtractor}

import scala.runtime.BoxedUnit

class DefinitionExtractor[T](methodDefinitionExtractor: MethodDefinitionExtractor[T]) {

  def extract(obj: T, methodDef: MethodDefinition, categories: List[String]): ObjectDefinition = {
    ObjectDefinition(
      methodDef.orderedParameters.definedParameters,
      Typed(methodDef.returnType),
      categories
    )
  }

  def extractMethodDefinition(obj: T): MethodDefinition = {
    methodDefinitionExtractor.extractMethodDefinition(obj, findMethodToInvoke(obj))
      .fold(msg => throw new IllegalArgumentException(msg), identity)
  }

  private def findMethodToInvoke(obj: T): Method = {
    val methodsToInvoke = obj.getClass.getMethods.toList.filter { m =>
      m.getAnnotation(classOf[MethodToInvoke]) != null
    }
    methodsToInvoke match {
      case Nil =>
        throw new IllegalArgumentException(s"Missing method to invoke for object: " + obj)
      case head :: Nil =>
        head
      case moreThanOne =>
        throw new IllegalArgumentException(s"More than one method to invoke: " + moreThanOne + " in object: " + obj)
    }
  }

}

object DefinitionExtractor {
  import TypeInfos._

  trait ObjectMetadata {
    def parameters: List[Parameter]

    def returnType: TypingResult

    def categories: List[String]

    def hasNoReturn : Boolean = Set(Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(returnType)

  }

  case class ObjectWithMethodDef(obj: Any,
                                 methodDef: MethodDefinition,
                                 objectDefinition: ObjectDefinition) extends ObjectMetadata with LazyLogging {
    def invokeMethod(paramFun: String => Option[AnyRef], additional: Seq[AnyRef]) : Any = {
      val values = methodDef.orderedParameters.prepareValues(paramFun, additional)
      try {
        methodDef.invocation(obj, values)
      } catch {
        case ex: IllegalArgumentException =>
          //this indicates that parameters do not match or argument list is incorrect
          logger.warn(s"Failed to invoke method: ${methodDef.name}, with params: $values", ex)
          throw ex
        //this is somehow an edge case - normally service returns failed future for exceptions
        case ex: InvocationTargetException =>
          throw ex.getTargetException
      }
    }

    override def parameters = objectDefinition.parameters

    override def categories = objectDefinition.categories

    override def returnType = objectDefinition.returnType

    def as[T] : T = obj.asInstanceOf[T]

  }

  case class PlainClazzDefinition(clazzName: ClazzRef, methods: Map[String, ClazzRef]) {
    def getMethod(methodName: String): Option[ClazzRef] = {
      methods.get(methodName)
    }
  }

  case class ObjectDefinition(parameters: List[Parameter],
                              returnType: TypingResult, categories: List[String]) extends ObjectMetadata

  object Parameter {
    def unknownType(name: String) = Parameter(name, ClazzRef[Any], ClazzRef[Any])

    def apply(name: String, typ: ClazzRef): Parameter = Parameter(name, typ, typ)

  }



  case class Parameter(name: String, typ: ClazzRef, originalType: ClazzRef, restriction: Option[ParameterRestriction] = None)

  //TODO: add validation of restrictions during compilation...
  //this can be used for different restrictions than list of values, e.g. encode '> 0' conditions and so on...
  sealed trait ParameterRestriction

  case class StringValues(values: List[String]) extends ParameterRestriction

  object ObjectWithMethodDef {
    def apply[T](obj: WithCategories[_<:T], methodExtractor: MethodDefinitionExtractor[T]): ObjectWithMethodDef = {
      val objectExtractor = new DefinitionExtractor(methodExtractor)
      val methodDefinition = objectExtractor.extractMethodDefinition(obj.value)
      ObjectWithMethodDef(obj.value, methodDefinition, objectExtractor.extract(obj.value, methodDefinition, obj.categories))
    }
  }

  object TypesInformation {
    def extract(services: Iterable[ObjectWithMethodDef],
                sourceFactories: Iterable[ObjectWithMethodDef],
                customNodeTransformers: Iterable[ObjectWithMethodDef],
                signalsFactories: Iterable[ObjectWithMethodDef],
                globalProcessVariables: Iterable[ClazzRef])
               (implicit settings: ClassExtractionSettings): List[ClazzDefinition] = {

      //TODO: do we need services here?
      val classesToExtractDefinitions =
      globalProcessVariables ++
        (services ++ customNodeTransformers ++ sourceFactories ++ signalsFactories).map(sv => sv.methodDef.returnType)

      TypesInformationExtractor.clazzAndItsChildrenDefinition(classesToExtractDefinitions)
    }
  }

  object ObjectDefinition {

    def noParam: ObjectDefinition = ObjectDefinition(List.empty, Typed[Null], List())

    def withParams(params: List[Parameter]): ObjectDefinition = ObjectDefinition(params, Typed[Null], List())

    def withParamsAndCategories(params: List[Parameter], categories: List[String]): ObjectDefinition =
      ObjectDefinition(params, Typed[Null], categories)

    def apply(parameters: List[Parameter], returnType: ClazzRef, categories: List[String]): ObjectDefinition = {
      ObjectDefinition(parameters, Typed(returnType), categories)
    }
  }

}

object TypeInfos {

  //FIXME we should use ClazzRef instead of String here, but it will require some frontend changes
  case class Parameter private(name: String, refClazz: ClazzRef, refClazzName: String)
  object Parameter {
    def apply(name: String, clazz: ClazzRef): Parameter = {
      new Parameter(name, clazz, clazz.refClazzName)
    }
  }

  //FIXME we should use ClazzRef instead of String here, but it will require some frontend changes
  case class MethodInfo private(parameters: List[Parameter], refClazz: ClazzRef, refClazzName: String, description: Option[String])
  object MethodInfo {
    def apply(parameters: List[Parameter], returnType: ClazzRef, description: Option[String]): MethodInfo = {
      new MethodInfo(parameters, returnType, returnType.refClazzName, description)
    }
  }

  case class ClazzDefinition(clazzName: ClazzRef, methods: Map[String, MethodInfo]) {
    def getMethod(methodName: String): Option[ClazzRef] = {
      methods.get(methodName).map(_.refClazz)
    }
  }

}