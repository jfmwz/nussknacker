package pl.touk.nussknacker.ui.process.uiconfig.defaults

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.typed.ClazzRef
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.ui.api.NodeDefinition

class ConfigParameterDefaultValueExtractorTest extends FlatSpec with Matchers {
  private val config = new ParamDefaultValueConfig(Map("definedNode" -> Map("definedParam" -> "Idea")))
  private val extractor = new ConfigParameterDefaultValueExtractor(config)
  private val node = NodeDefinition("definedNode", Nil)
  behavior of "ConfigParameterDefaultValueExtractor"

  private def verifyExtractor(paramName: String, ofType: ClazzRef, evaluatesTo: Option[String]) = {
    val param = DefinitionExtractor.Parameter(paramName, ofType)
    it should s"evaluate $param to $evaluatesTo" in {
      extractor.evaluateParameterDefaultValue(node, param) shouldBe evaluatesTo
    }
  }

  verifyExtractor("undefinedParameter", ofType = ClazzRef(Integer.TYPE), evaluatesTo = None)
  verifyExtractor("definedParam", ofType = ClazzRef(Integer.TYPE), evaluatesTo = Some("Idea"))
}
