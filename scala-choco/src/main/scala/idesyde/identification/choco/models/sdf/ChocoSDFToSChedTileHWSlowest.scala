package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoCPForSyDeDecisionModel
import org.chocosolver.solver.Model
import forsyde.io.java.core.Vertex
import org.chocosolver.solver.Solution
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult
import idesyde.identification.choco.models.ManyProcessManyMessageMemoryConstraintsMixin
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import idesyde.identification.forsyde.models.mixed.SDFToSchedTiledHW
import idesyde.identification.forsyde.ForSyDeIdentificationRule
import idesyde.identification.choco.models.TileAsyncInterconnectCommsModule
import spire.math.Rational
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.selectors.variables.Largest
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMedian
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMax
import idesyde.identification.choco.models.SingleProcessSingleMessageMemoryConstraintsModule
import org.chocosolver.solver.search.strategy.strategy.FindAndProve
import idesyde.identification.choco.interfaces.ChocoModelMixin

final case class ChocoSDFToSChedTileHWSlowest(
    val dse: SDFToSchedTiledHW
)(using Fractional[Rational])
    extends ChocoCPForSyDeDecisionModel
    with ChocoModelMixin(shouldLearnSignedClauses = false) {

  val chocoModel: Model = Model()

  // section for time multiplier calculation
  val timeValues =
    (dse.wcets.flatten ++ dse.platform.tiledDigitalHardware.maxTraversalTimePerBitPerRouter)
  var timeMultiplier = 1L
  while (
    timeValues
      .map(t => t * (timeMultiplier))
      .exists(d =>
        d.numerator <= d.denominator / 100L
      ) // ensure that the numbers magnitudes still stay sane
    &&
    timeValues
      .map(t => t * (timeMultiplier))
      .sum < Int.MaxValue / 100 - 1
  ) {
    timeMultiplier *= 10
  }

  // do the same for memory numbers
  val memoryValues = dse.platform.tiledDigitalHardware.memories.map(_.getSpaceInBits().toLong) ++
    dse.sdfApplications.messagesMaxSizes ++
    dse.sdfApplications.processSizes
  var memoryDivider = 1L
  while (memoryValues.forall(_ / memoryDivider >= 100) && memoryDivider < Int.MaxValue) {
    memoryDivider *= 10L
  }
  // scribe.debug(timeMultiplier.toString)

  val memoryMappingModule = SingleProcessSingleMessageMemoryConstraintsModule(
    chocoModel,
    dse.sdfApplications.processSizes.map(_ / memoryDivider).map(_.toInt),
    dse.sdfApplications.sdfMessages.map((_, _, _, mSize, _, _, _) => (mSize / memoryDivider).toInt),
    dse.platform.tiledDigitalHardware.maxMemoryPerTile
      .map(_ / memoryDivider)
      .map(l => if (l > Int.MaxValue) then Int.MaxValue - 1 else l)
      .map(_.toInt)
  )

  val tileAnalysisModule = TileAsyncInterconnectCommsModule(
    chocoModel,
    dse.platform.schedulerSet,
    dse.platform.tiledDigitalHardware.routerSet,
    dse.sdfApplications.sdfMessages.zipWithIndex.map((m, i) => i),
    dse.sdfApplications.sdfMessages.map((_, _, _, mSize, _, _, _) =>
      dse.platform.tiledDigitalHardware.bandWidthPerCEPerVirtualChannel.map(bw =>
        (mSize / bw / timeMultiplier / memoryDivider).ceil.toInt
      )
    ),
    dse.platform.tiledDigitalHardware.commElemsVirtualChannels,
    dse.platform.tiledDigitalHardware.computeRouterPaths
    // dse.platform.tiledDigitalHardware.routerSet.zipWithIndex.map((_, src) =>
    //   dse.platform.tiledDigitalHardware.routerSet.zipWithIndex.map((_, dst) =>
    //     dse.platform.tiledDigitalHardware.commElemsVirtualChannels(
    //       src
    //     ) == dse.platform.tiledDigitalHardware.commElemsVirtualChannels(dst)
    //   )
    // ),
    // memoryMappingModule.messagesMemoryMapping
  )

  val sdfAnalysisModule = SDFSchedulingAnalysisModule(
    chocoModel,
    dse,
    memoryMappingModule,
    tileAnalysisModule,
    timeMultiplier
  )

  //-----------------------------------------------------
  // Decision variables

  //---------

  // in a tile-based architecture, a process being mapped to a memory tile implies it is scheduled there too!

  //-----------------------------------------------------
  // AUXILIARY VARIABLES

  //---------

  //-----------------------------------------------------
  // MAPPING

  // - mixed constraints
  memoryMappingModule.postSingleProcessSingleMessageMemoryConstraints()

  //---------

  //-----------------------------------------------------
  // COMMUNICATION

  // we make sure that the input messages are always mapped with the consumers so that
  // it would be synthetizeable later. Otherwise the model becomes irrealistic
  memoryMappingModule.processesMemoryMapping.zipWithIndex.foreach((aMap, a) => {
    memoryMappingModule.messagesMemoryMapping.zipWithIndex.foreach((cMap, c) => {
      val (s, t, cs, _, _, _, _) = dse.sdfApplications.sdfMessages(c)
      if (a == t) {
        chocoModel.arithm(aMap, "=", cMap).post()
      } else if (a == s) {
        // build the table that make this constraint
        dse.platform.schedulerSet.zipWithIndex.foreach((_, sendi) => {
          dse.platform.schedulerSet.zipWithIndex.foreach((_, desti) => {
            if (sendi != desti) {
              chocoModel.ifThen(
                chocoModel.and(
                  chocoModel.arithm(aMap, "=", sendi),
                  chocoModel.arithm(cMap, "=", desti)
                ),
                chocoModel.arithm(
                  tileAnalysisModule.procElemSendsDataToAnother(sendi)(desti),
                  "=",
                  1
                )
              )
            }
          })
        })
      }
    })
  })

  tileAnalysisModule.postTileAsyncInterconnectComms()
  //---------

  //-----------------------------------------------------
  // SCHEDULING AND TIMING

  // and sdf can be executed in a PE only if its mapped into this PE
  dse.sdfApplications.actorsSet.zipWithIndex.foreach((_, a) => {
    dse.platform.schedulerSet.zipWithIndex.foreach((_, p) => {
      chocoModel.ifThen(
        chocoModel.sum(sdfAnalysisModule.firingsInSlots(a)(p), ">", 0),
        chocoModel.arithm(memoryMappingModule.processesMemoryMapping(a), "=", p)
      )
      // chocoModel.ifThen(
      //   memoryMappingModule.processesMemoryMapping(a).ne(p).decompose(),
      //   chocoModel
      //     .sum(s"firings_${a}_${p}=0", sdfAnalysisModule.firingsInSlots(a)(p): _*)
      //     .eq(0)
      //     .decompose()
      // )
    })
  })

  sdfAnalysisModule.postSDFTimingAnalysisSAS()
  //---------

  //-----------------------------------------------------
  // Objectives

  // remember that this is tiled based, so a memory being mapped entails the processor
  // is used
  val nUsedPEs = chocoModel.intVar(
    "nUsedPEs",
    1,
    dse.platform.tiledDigitalHardware.processors.length,
    true
  )
  // make sure the variable counts the number of used
  // chocoModel.atMostNValues(memoryMappingModule.processesMemoryMapping, nUsedPEs, true).post()
  // chocoModel.atLeastNValues(memoryMappingModule.processesMemoryMapping, nUsedPEs, true).post()
  chocoModel.nValues(memoryMappingModule.processesMemoryMapping, nUsedPEs).post()

  override val modelMinimizationObjectives: Array[IntVar] =
    Array(
      nUsedPEs,
      sdfAnalysisModule.globalInvThroughput
      // chocoModel.max("maximumBuffer", sdfAnalysisModule.slotRange.map(s => chocoModel.sum(s"tokensAt($s)", sdfAnalysisModule.tokens.map(bc => bc(s)):_*)))
    )
  //---------

  //-----------------------------------------------------
  // BRANCHING AND SEARCH

  // val listScheduling = SimpleMultiCoreSDFListScheduling(
  //   dse.sdfApplications.actorsSet.zipWithIndex.map((a, i) => dse.sdfApplications.sdfRepetitionVectors(i)),
  //   dse.sdfApplications.sdfBalanceMatrix,
  //   dse.sdfApplications.initialTokens,
  //   dse.wcets.map(ws => ws.map(w => w * timeMultiplier).map(_.ceil.intValue)),
  //   tileAnalysisModule.messageTravelDuration,
  //   sdfAnalysisModule.firingsInSlots
  // )

  override val strategies: Array[AbstractStrategy[? <: Variable]] = chocoModel
    .getVars()
    .filter(v => v.isInstanceOf[IntVar])
    .map(v => Search.minDomLBSearch(v.asInstanceOf[IntVar]))

  //---------

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    val paths = tileAnalysisModule.commElemsPaths
    val channelToRouters = dse.sdfApplications.channelsSet.map(c =>
      val i = dse.sdfApplications.sdfMessages.indexWhere((s, d, cs, l, _, _, _) => cs.contains(c))
      dse.platform.tiledDigitalHardware.routerSet.zipWithIndex.map((s, j) =>
        val p = output.getIntVal(memoryMappingModule.messagesMemoryMapping(i))
        output.getIntVal(tileAnalysisModule.numVirtualChannelsForProcElem(p)(j)) > 0
      )
    )
    // println(dse.platform.tiledDigitalHardware.routerPaths.map(_.map(_.mkString("[", ", ", "]")).mkString("[", ", ", "]")).mkString("[", "\n", "]"))
    // println(channelToRouters.map(_.mkString("[", ", ", "]")).mkString("[", "\n", "]"))
    val channelToTiles = dse.sdfApplications.channelsSet.map(c =>
      val i = dse.sdfApplications.sdfMessages.indexWhere((s, d, cs, l, _, _, _) => cs.contains(c))
      dse.platform.tiledDigitalHardware.tileSet.zipWithIndex.map((s, j) =>
        output.getIntVal(memoryMappingModule.messagesMemoryMapping(i)) == j
      )
    )
    val mappings = dse.sdfApplications.channels.zipWithIndex
      .map((c, i) => channelToTiles(i) ++ channelToRouters(i))
    val schedulings =
      memoryMappingModule.processesMemoryMapping.map(vs =>
        dse.platform.tiledDigitalHardware.tileSet.map(j => output.getIntVal(vs) == j)
      )
    // println(
    //   dse.platform.schedulerSet.zipWithIndex
    //     .map((_, s) => {
    //       (0 until dse.sdfApplications.actors.size)
    //         .map(slot => {
    //           dse.sdfApplications.actors.zipWithIndex
    //             .find((a, ai) => sdfAnalysisModule.firingsInSlots(ai)(s)(slot).getLB() > 0)
    //             .map((a, ai) =>
    //               a.getIdentifier() + ": " + output
    //                 .getIntVal(sdfAnalysisModule.firingsInSlots(ai)(s)(slot))
    //             )
    //             .getOrElse("_")
    //         })
    //         .mkString("[", ", ", "]")
    //     })
    //     .mkString("[\n ", "\n ", "\n]")
    // )
    // println(
    //   dse.platform.schedulerSet.zipWithIndex
    //     .map((_, src) => {
    //       dse.platform.schedulerSet.zipWithIndex
    //         .map((_, dst) => {
    //           dse.sdfApplications.channels.zipWithIndex
    //             .filter((c, ci) =>
    //               tileAnalysisModule.messageIsCommunicated(ci)(src)(dst).getLB() > 0
    //             )
    //             .map((c, ci) =>
    //               c.getIdentifier() + ": " + paths(src)(dst).zipWithIndex
    //                 .map((ce, cei) =>
    //                   ce + "/" + output
    //                     .getIntVal(tileAnalysisModule.virtualChannelForMessage(ci)(cei))
    //                 )
    //                 .mkString("-")
    //             )
    //             .mkString("(", ", ", ")")
    //         })
    //         .mkString("[", ", ", "]")
    //     })
    //     .mkString("[\n ", "\n ", "\n]")
    // )
    dse.addMappingsAndRebuild(
      mappings,
      schedulings,
      Array.empty,
      // TODO: fix this slot allocaiton strategy for later. It is Okay, but lacks some direct synthetizable details, like which exact VC the channel goes
      dse.sdfApplications.channelsSet.zipWithIndex.map((c, ci) => {
        val i = dse.sdfApplications.sdfMessages.indexWhere((s, d, cs, l, _, _, _) => cs.contains(c))
        dse.platform.tiledDigitalHardware.allCommElems.zipWithIndex.map((ce, j) => {
          val p = output.getIntVal(memoryMappingModule.messagesMemoryMapping(i))
          if (output.getIntVal(tileAnalysisModule.numVirtualChannelsForProcElem(p)(j)) > 0) p else 0
        })
      }),
      dse.sdfApplications.actors.zipWithIndex.map((a, i) =>
        Rational(
          timeMultiplier,
          sdfAnalysisModule
            .invThroughputs(
              memoryMappingModule.processesMemoryMapping(i).getValue()
            )
            .getValue()
        )
      )
    )
  }

  def uniqueIdentifier: String = "ChocoSDFToSChedTileHWSlowest"

  def coveredVertexes: Iterable[Vertex] = dse.coveredVertexes

}

object ChocoSDFToSChedTileHWSlowest {

  def identifyFromAny(
      model: Any,
      identified: scala.collection.Iterable[DecisionModel]
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHWSlowest] =
    ForSyDeIdentificationRule.identifyWrapper(model, identified, identifyFromForSyDe)
  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHWSlowest] = {
    identified
      .find(m => m.isInstanceOf[SDFToSchedTiledHW])
      .map(m => m.asInstanceOf[SDFToSchedTiledHW])
      .map(dse => identFromForSyDeWithDeps(model, dse))
      .getOrElse(IdentificationResult.unfixedEmpty())
  }

  def identFromForSyDeWithDeps(
      model: ForSyDeSystemGraph,
      dse: SDFToSchedTiledHW
  )(using scala.math.Fractional[Rational]): IdentificationResult[ChocoSDFToSChedTileHWSlowest] = {
    IdentificationResult.fixed(ChocoSDFToSChedTileHWSlowest(dse))
  }

}
