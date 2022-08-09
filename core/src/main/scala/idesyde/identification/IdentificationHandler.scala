package idesyde.identification

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel

import java.util.stream.Collectors

import scala.collection.mutable.HashSet

import collection.JavaConverters.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import scala.collection.mutable.Buffer
import idesyde.utils.CoreUtils

class IdentificationHandler(
    var registeredModules: Set[IdentificationModule] = Set(),
    val infoLogger: (String) => Unit = (s => println(s)),
    val debugLogger: (String) => Unit = (s => println(s))
) {

  def registerIdentificationRule(identModule: IdentificationModule): IdentificationHandler =
    registeredModules += identModule
    this

  def identifyDecisionModels[DesignModel](
      model: DesignModel
  ): Set[DecisionModel] = {
    var identified: Set[DecisionModel] = Set()
    var activeRules                    = registeredModules.flatMap(m => m.identificationRules)
    var iters                          = 0
    // val dominanceGraph = SimpleDirectedGraph[DecisionModel, DefaultEdge](classOf[DefaultEdge])
    var prevIdentified = -1
    // val reachability: Buffer[Buffer[Boolean]] = Buffer.empty
    infoLogger(
      s"Performing identification with ${activeRules.size} rules."
    )
    while (activeRules.size > 0 && prevIdentified < identified.size) {
      prevIdentified = identified.size
      val ruleResults = activeRules.map(irule => (irule, irule(model, identified)))
      def newIdentified =
        ruleResults.flatMap((irule, res) => res.identified).toSet
      // for (m <- newIdentified) dominanceGraph.addVertex(m)
      // add the new rows to the identification matrix
      // reachability ++=
      //   newIdentified.map(m => identified.map(mm => m.dominates(mm, model)).toBuffer).toBuffer
      // // expand the previous rows with a possible dominance or not
      // identified.zipWithIndex.foreach((m, i) => {
      //   reachability(i) ++= newIdentified.map(mm => m.dominates(mm, model)).toBuffer
      // })
      // for (m <- newIdentified; mm <- identified; if m.dominates(mm, model))
      //   dominanceGraph.addEdge(m, mm)
      // for (m <- identified; mm <- newIdentified; if m.dominates(mm, model))
      //   dominanceGraph.addEdge(m, mm)
      // finally add to the current identified
      identified = identified ++ newIdentified
      // identified =
      //   identified.filter(m => !identified.exists(other => other != m && other.dominates(m)))
      activeRules = ruleResults.filter((irule, res) => !res.isFixed()).map((irule, _) => irule)
      debugLogger(
        s"identification step $iters: ${identified.size} identified and ${activeRules.size} rules"
      )
      iters += 1
    }
    // build reachability matrix
    val identifiedArray = identified.toArray
    def reachability = identifiedArray.map(m => identifiedArray.map(mm => m.dominates(mm, model)))
    def reachibilityClosure =
      CoreUtils.reachibilityClosure(reachability.map(row => row.toArray).toArray)
    // val dominanceCondensation = GabowStrongConnectivityInspector(dominanceGraph).getCondensation()
    // keep only the SCC which are leaves
    val dominanceComponents = CoreUtils.computeSSCFromReachibility(reachibilityClosure)
    val dominant = dominanceComponents
      .filter(component => {
        // keep dominant components in which no other components dominate any decision model
        // therein
        dominanceComponents
          .filter(_ != component)
          .forall(other => {
            // decision models in component
            def componentModels = component.map(identifiedArray(_))
            def otherModels     = other.map(identifiedArray(_))
            !componentModels.exists(m => otherModels.exists(mm => mm.dominates(m, model)))
          })
      })
      .flatMap(component => component.map(idx => identifiedArray(idx)))
      .toSet
    infoLogger(s"dropped ${identified.size - dominant.size} dominated decision model(s).")
    debugLogger(s"dominant: ${dominant.map(m => m.uniqueIdentifier)}")
    dominant
  }

}
