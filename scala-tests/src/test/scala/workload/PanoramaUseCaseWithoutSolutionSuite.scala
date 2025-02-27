package workload

import org.scalatest.funsuite.AnyFunSuite
import idesyde.exploration.ExplorationHandler
import idesyde.identification.IdentificationHandler
import idesyde.exploration.ChocoExplorationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.api.ForSyDeIdentificationModule
import idesyde.identification.minizinc.api.MinizincIdentificationModule
import forsyde.io.java.drivers.ForSyDeModelHandler
import forsyde.io.java.amalthea.drivers.ForSyDeAmaltheaDriver
import java.nio.file.Paths
import scribe.Level
import idesyde.exploration.forsyde.interfaces.ForSyDeIOExplorer
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext

import mixins.LoggingMixin

class PanoramaUseCaseWithoutSolutionSuite extends AnyFunSuite with LoggingMixin {

  given ExecutionContext = ExecutionContext.global

  setNormal()

  val explorationHandler = ExplorationHandler(
    infoLogger = (s: String) => scribe.info(s),
    debugLogger = (s: String) => scribe.debug(s)
  )
    .registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler(
    infoLogger = (s: String) => scribe.info(s),
    debugLogger = (s: String) => scribe.debug(s)
  )
    .registerIdentificationRule(ChocoIdentificationModule())
    .registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())

  val forSyDeModelHandler = ForSyDeModelHandler().registerDriver(ForSyDeAmaltheaDriver())

  val flightInfo = forSyDeModelHandler.loadModel(
    Paths.get("scala-tests/models/panorama/flight-information-system.amxmi")
  )
  val radar = forSyDeModelHandler.loadModel(
    Paths.get("scala-tests/models/panorama/radar-system.amxmi")
  )
  val bounds =
    forSyDeModelHandler.loadModel(Paths.get("scala-tests/models/panorama/utilizationBounds.forsyde.xmi"))
  val model           = flightInfo.merge(radar).merge(bounds)
  lazy val identified = identificationHandler.identifyDecisionModels(model)
  lazy val chosen     = explorationHandler.chooseExplorersAndModels(identified)

  test("PANORAMA case study without any solutions - At least 1 decision model") {
    assert(identified.size > 0)
  }

  test("PANORAMA case study without any solutions - At least 1 combo") {
    assert(chosen.size > 0)
  }

  test("PANORAMA case study without any solutions - no solution found") {
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .flatMap(designModel => designModel match {case f: ForSyDeSystemGraph => Some(f); case _ => Option.empty})
          .map(sol =>
            forSyDeModelHandler
              .writeModel(model.merge(sol), "scala-tests/models/panorama/wrong_output_of_dse.fiodl")
            sol
          ).take(1)
      )
    assert(solutions.size == 0)
  }

}
