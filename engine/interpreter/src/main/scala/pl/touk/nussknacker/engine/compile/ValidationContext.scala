package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data.{ValidatedNel, _}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.{NoParentContext, NodeId, OverwrittenVariable}
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.definition.TypeInfos.ClazzDefinition

object ValidationContext {

  def empty = ValidationContext()
}

case class ValidationContext(variables: Map[String, TypingResult] = Map.empty,
                             parent: Option[ValidationContext] = None) {

  def apply(name: String): TypingResult =
    get(name).getOrElse(throw new RuntimeException(s"Unknown variable: $name"))

  def get(name: String): Option[TypingResult] =
    variables.get(name)

  def contains(name: String): Boolean = variables.contains(name)

  def withVariable(name: String, value: TypingResult)(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, ValidationContext] =
    if (variables.contains(name)) Invalid(NonEmptyList.of(OverwrittenVariable(name))) else Valid(copy(variables = variables + (name -> value)))

  def pushNewContext() : ValidationContext
    = ValidationContext(Map(), Some(this))

  def popContext(implicit nodeId: NodeId) : ValidatedNel[PartSubGraphCompilationError, ValidationContext] = parent match {
    case Some(ctx) => Valid(ctx)
    case None => Invalid(NonEmptyList.of(NoParentContext(nodeId.id)))
  }

}
