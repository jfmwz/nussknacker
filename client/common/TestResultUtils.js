import _ from 'lodash'
import NodeUtils from '../components/graph/NodeUtils'

class TestResultUtils {


  resultsForNode = (testResults, nodeId) => {
    if (testResults && this._nodeResults(testResults, nodeId)) {
      return {
        invocationResults: this._invocationResults(testResults, nodeId),
        mockedResults: this._mockedResults(testResults, nodeId),
        nodeResults: this._nodeResults(testResults, nodeId),
        errors: this._errors(testResults, nodeId)
      }
    } else {
      return null;
    }
  }

  nodeResultsSummary = (testResults, node) => {
    const ids = NodeUtils.nodeIsGroup(node) ? node.ids : [ node.id ]
    return _.reduce(ids.map(id => this.resultsForNode(testResults, id)),
      (acc, n) => ({all: Math.max(n.nodeResults.length, acc.all), errors: Math.max(n.errors.length, acc.errors)}),
      {all: 0, errors: 0}
    )
  }

  _nodeResults = (testResults, nodeId) => {
    return (testResults.nodeResults || {})[nodeId] || []
  }

  _invocationResults = (testResults, nodeId) => {
    return (testResults.invocationResults || {})[nodeId] || []
  }

  _mockedResults = (testResults, nodeId) => {
    return (testResults.mockedResults || {})[nodeId] || []
  }

  _errors = (testResults, nodeId) => {
    return (testResults.exceptions || []).filter((ex) => ex.nodeId == nodeId)
  }

  nodeResultsForContext = (nodeTestResults, contextId) => {
    var context = (nodeTestResults.nodeResults.find(result => result.context.id == contextId) || {}).context
    var expressionResults = _.fromPairs(nodeTestResults
      .invocationResults
      .filter(result => result.context.id == contextId)
      .map(result => [result.name, result.value]))
    var mockedResultsForCurrentContext = nodeTestResults.mockedResults.filter(result => result.context.id == contextId)
    var mockedResultsForEveryContext = nodeTestResults.mockedResults
    var error = ((nodeTestResults.errors || []).find((error) => error.context.id == contextId) || {}).exception
    return {
      context: context,
      expressionResults: expressionResults,
      mockedResultsForCurrentContext: mockedResultsForCurrentContext,
      mockedResultsForEveryContext: mockedResultsForEveryContext,
      error: error
    }
  }

  availableContexts = (testResults) => {
    var varToInclude = "input"
    return _.uniq(testResults.nodeResults.map(nr => ({id: nr.context.id, [varToInclude]: nr.context.variables[varToInclude]})))

  }
}

export default new TestResultUtils()