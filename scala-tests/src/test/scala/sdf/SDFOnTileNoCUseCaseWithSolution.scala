package sdf

import scala.jdk.CollectionConverters.*

import org.scalatest.funsuite.AnyFunSuite
import idesyde.exploration.ExplorationHandler
import idesyde.identification.IdentificationHandler
import forsyde.io.java.drivers.ForSyDeModelHandler
import scala.concurrent.ExecutionContext
import idesyde.identification.common.CommonIdentificationModule
import idesyde.identification.choco.ChocoIdentificationModule
import idesyde.identification.forsyde.ForSyDeIdentificationModule
import idesyde.identification.minizinc.MinizincIdentificationModule
import idesyde.exploration.ChocoExplorationModule
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.core.EdgeTrait
import forsyde.io.java.typed.viewers.visualization.Visualizable
import forsyde.io.java.typed.viewers.platform.AbstractStructure
import forsyde.io.java.typed.viewers.visualization.GreyBox
import idesyde.identification.common.models.sdf.SDFApplication
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import forsyde.io.java.typed.viewers.decision.Allocated
import idesyde.identification.forsyde.ForSyDeDesignModel
import idesyde.utils.Logger
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.mixed.SDFToTiledMultiCore

import mixins.LoggingMixin
import forsyde.io.java.graphviz.drivers.ForSyDeGraphVizDriver
import forsyde.io.java.kgt.drivers.ForSyDeKGTDriver
import forsyde.io.java.sdf3.drivers.ForSyDeSDF3Driver
import java.nio.file.Files
import java.nio.file.Paths
import org.scalatest.Tag
import org.scalatest.Ignore
import idesyde.utils.SimpleStandardIOLogger
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHW2
import tags.ResourceHungry

/** This test suite uses as much as possible the experiments from the paper
  *
  * K. Rosvall, T. Mohammadat, G. Ungureanu, J. Öberg, and I. Sander, “Exploring Power and
  * Throughput for Dataflow Applications on Predictable NoC Multiprocessors,” Aug. 2018, pp.
  * 719–726. doi: 10.1109/DSD.2018.00011.
  *
  * Which are mostly (all?) present in the DeSyDe source code repository
  * https://github.com/forsyde/DeSyDe, in its examples folder.
  */
class SDFOnTileNoCUseCaseWithSolution extends AnyFunSuite with LoggingMixin {

  given ExecutionContext = ExecutionContext.global

  setNormal()

  val solutionsTaken = 1

  Files.createDirectories(Paths.get("models/sdf3/results"))

  given Logger = SimpleStandardIOLogger

  val explorationHandler = ExplorationHandler(
  ).registerModule(ChocoExplorationModule())

  val identificationHandler = IdentificationHandler(
  ).registerIdentificationRule(ForSyDeIdentificationModule())
    .registerIdentificationRule(MinizincIdentificationModule())
    .registerIdentificationRule(CommonIdentificationModule())
    .registerIdentificationRule(ChocoIdentificationModule())

  val forSyDeModelHandler = ForSyDeModelHandler()
    .registerDriver(ForSyDeSDF3Driver())
    .registerDriver(ForSyDeKGTDriver())
    .registerDriver(ForSyDeGraphVizDriver())

  // the platform is done here in memory since the format used by the DeSyDe tool is non-standard,
  // even for the SDF3 group.
  val small2x2PlatformModel = {
    val m      = ForSyDeSystemGraph()
    var niMesh = Array.fill(2)(Array.fill[InstrumentedCommunicationModule](2)(null))
    // put the microblaze elements
    for (i <- 0 until 3; row = i % 2; col = (i - row) / 2) {
      val tile     = AbstractStructure.enforce(m.newVertex("tile_" + i))
      val tileVisu = GreyBox.enforce(tile)
      val proc =
        InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_" + i)))
      val mem =
        GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_mem" + i)))
      val ni = InstrumentedCommunicationModule.enforce(
        Visualizable.enforce(m.newVertex("micro_blaze_ni" + i))
      )
      val router = InstrumentedCommunicationModule.enforce(
        Visualizable.enforce(m.newVertex("router" + i))
      )
      val scheduler  = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_os" + i))
      val niSchedule = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_ni_slots" + i))
      val routerSchedule =
        StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_router_slots" + i))
      proc.setOperatingFrequencyInHertz(50000000L)
      mem.setOperatingFrequencyInHertz(50000000L)
      mem.setSpaceInBits(1048576L * 8L)
      ni.setOperatingFrequencyInHertz(50000000L)
      ni.setFlitSizeInBits(128L)
      ni.setMaxConcurrentFlits(4)
      ni.setMaxCyclesPerFlit(4)
      ni.setInitialLatency(0L)
      router.setOperatingFrequencyInHertz(50000000L)
      router.setFlitSizeInBits(128L)
      router.setMaxConcurrentFlits(4)
      router.setMaxCyclesPerFlit(4)
      router.setInitialLatency(0L)
      proc.setModalInstructionsPerCycle(
        Map(
          "eco" -> Map(
            "all" -> (1.0 / 65.0).asInstanceOf[java.lang.Double]
          ).asJava,
          "default" -> Map(
            "all" -> (1.0 / 13.0).asInstanceOf[java.lang.Double]
          ).asJava
        ).asJava
      )
      // connect them
      tile.insertSubmodulesPort(m, proc)
      tileVisu.insertContainedPort(m, Visualizable.enforce(proc))
      tile.insertSubmodulesPort(m, mem)
      tileVisu.insertContainedPort(m, Visualizable.enforce(mem))
      tile.insertSubmodulesPort(m, ni)
      tileVisu.insertContainedPort(m, Visualizable.enforce(ni))
      proc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
      mem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
      ni.getViewedVertex().addPorts("tileMemory", "tileProcessor", "router")
      router.getViewedVertex().addPorts("tileNI")
      niMesh(row)(col) = router
      m.connect(
        proc,
        ni,
        "networkInterface",
        "tileProcessor",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        proc,
        "tileProcessor",
        "networkInterface",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        proc,
        mem,
        "defaultMemory",
        "instructionsAndData",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        mem,
        proc,
        "instructionsAndData",
        "defaultMemory",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        mem,
        ni,
        "networkInterface",
        "tileMemory",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        mem,
        "tileMemory",
        "networkInterface",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        router,
        ni,
        "tileNI",
        "router",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        router,
        "router",
        "tileNI",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      GreyBox.enforce(proc).insertContainedPort(m, Visualizable.enforce(scheduler))
      Allocated.enforce(scheduler).insertAllocationHostsPort(m, proc)
      Allocated.enforce(niSchedule).insertAllocationHostsPort(m, ni)
      Allocated.enforce(routerSchedule).insertAllocationHostsPort(m, router)
    }
    // and now the Arm tile
    val armTile = AbstractStructure.enforce(m.newVertex("arm_tile"))
    val armProc = InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("arm_cpu")))
    val armMem  = GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("arm_mem")))
    val armNi = InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("arm_ni")))
    val armScheduler = StaticCyclicScheduler.enforce(m.newVertex("arm_os"))
    val armRouter =
      InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("arm_router")))
    val armNiScheduler     = StaticCyclicScheduler.enforce(m.newVertex("arm_ni_slots"))
    val armRouterScheduler = StaticCyclicScheduler.enforce(m.newVertex("arm_router_slots"))
    armProc.setOperatingFrequencyInHertz(666667000L)
    armMem.setOperatingFrequencyInHertz(666667000L)
    armMem.setSpaceInBits(4294967296L * 8L)
    armNi.setOperatingFrequencyInHertz(666667000L)
    armNi.setFlitSizeInBits(128L)
    armNi.setMaxConcurrentFlits(4)
    armNi.setMaxCyclesPerFlit(4)
    armNi.setInitialLatency(0L)
    armRouter.setOperatingFrequencyInHertz(666667000L)
    armRouter.setFlitSizeInBits(128L)
    armRouter.setMaxConcurrentFlits(4)
    armRouter.setMaxCyclesPerFlit(4)
    armRouter.setInitialLatency(0L)
    armProc.setModalInstructionsPerCycle(
      Map(
        "eco" -> Map(
          "all" -> (1.0 / 10.0).asInstanceOf[java.lang.Double]
        ).asJava,
        "default" -> Map(
          "all" -> (1.0).asInstanceOf[java.lang.Double]
        ).asJava
      ).asJava
    )
    // connect them
    val armTileVisu = GreyBox.enforce(armTile)
    armTile.insertSubmodulesPort(m, armProc)
    armTileVisu.insertContainedPort(m, Visualizable.enforce(armProc))
    armTile.insertSubmodulesPort(m, armMem)
    armTileVisu.insertContainedPort(m, Visualizable.enforce(armMem))
    armTile.insertSubmodulesPort(m, armNi)
    armTileVisu.insertContainedPort(m, Visualizable.enforce(armNi))
    armProc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
    armMem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
    armNi.getViewedVertex().addPorts("tileMemory", "tileProcessor", "router")
    armRouter.getViewedVertex().addPorts("tileNI")
    niMesh(1)(1) = armRouter
    m.connect(
      armProc,
      armNi,
      "networkInterface",
      "tileProcessor",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armNi,
      armProc,
      "tileProcessor",
      "networkInterface",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armProc,
      armMem,
      "defaultMemory",
      "instructionsAndData",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armMem,
      armProc,
      "instructionsAndData",
      "defaultMemory",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armMem,
      armNi,
      "networkInterface",
      "tileMemory",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armNi,
      armMem,
      "tileMemory",
      "networkInterface",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armRouter,
      armNi,
      "tileNI",
      "router",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    m.connect(
      armNi,
      armRouter,
      "router",
      "tileNI",
      EdgeTrait.PLATFORM_PHYSICALCONNECTION,
      EdgeTrait.VISUALIZATION_VISUALCONNECTION
    )
    GreyBox.enforce(armProc).insertContainedPort(m, Visualizable.enforce(armScheduler))
    Allocated.enforce(armScheduler).insertAllocationHostsPort(m, armProc)
    Allocated.enforce(armNiScheduler).insertAllocationHostsPort(m, armNi)
    Allocated.enforce(armRouterScheduler).insertAllocationHostsPort(m, armRouter)
    // and now we connect the NIs in the mesh
    for (i <- 0 until 4; row = i % 2; col = (i - row) / 2) {
      if (row > 0) {
        val r = row - 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
        niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(r)(col),
          s"to_${r}_${col}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(r)(col),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${r}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
      if (row < 1) {
        val r = row + 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
        niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(r)(col),
          s"to_${r}_${col}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(r)(col),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${r}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
      if (col > 0) {
        val c = col - 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
        niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(row)(c),
          s"to_${row}_${c}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(row)(c),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${c}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
      if (col < 1) {
        val c = col + 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
        niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(row)(c),
          s"to_${row}_${c}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(row)(c),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${c}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
    }

    m
  }

  /** This platform model simply abstracts all communication as a bus, conceptually equivalent to
    * the papers
    *
    * [1] K. Rosvall and I. Sander, “Flexible and Tradeoff-Aware Constraint-Based Design Space
    * Exploration for Streaming Applications on Heterogeneous Platforms,” ACM Trans. Des. Autom.
    * Electron. Syst., vol. 23, no. 2, p. 21:1-21:26, Nov. 2017, doi: 10.1145/3133210. [2] K.
    * Rosvall and I. Sander, “A constraint-based design space exploration framework for real-time
    * applications on MPSoCs,” in 2014 Design, Automation Test in Europe Conference Exhibition
    * (DATE), Mar. 2014, pp. 1–6. doi: 10.7873/DATE.2014.339.
    */
  val busLike8nodePlatformModel = {
    val m      = ForSyDeSystemGraph()
    var niMesh = Array.fill[InstrumentedCommunicationModule](8)(null)
    // put the microblaze elements
    for (i <- 0 until 8) {
      val tile     = AbstractStructure.enforce(m.newVertex("tile_" + i))
      val tileVisu = GreyBox.enforce(tile)
      val proc =
        InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_" + i)))
      val mem =
        GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("micro_blaze_mem" + i)))
      val ni = InstrumentedCommunicationModule.enforce(
        Visualizable.enforce(m.newVertex("micro_blaze_ni" + i))
      )
      val scheduler  = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_os" + i))
      val niSchedule = StaticCyclicScheduler.enforce(m.newVertex("micro_blaze_ni_slots" + i))
      proc.setOperatingFrequencyInHertz(50000000L)
      mem.setOperatingFrequencyInHertz(50000000L)
      mem.setSpaceInBits(1048576L * 8L)
      ni.setOperatingFrequencyInHertz(50000000L)
      ni.setFlitSizeInBits(128L)
      ni.setMaxConcurrentFlits(8)
      ni.setMaxCyclesPerFlit(8)
      ni.setInitialLatency(0L)
      proc.setModalInstructionsPerCycle(
        Map(
          "eco" -> Map(
            "all" -> (1.0 / 65.0).asInstanceOf[java.lang.Double]
          ).asJava,
          "default" -> Map(
            "all" -> (1.0 / 13.0).asInstanceOf[java.lang.Double]
          ).asJava
        ).asJava
      )
      // connect them
      tile.insertSubmodulesPort(m, proc)
      tileVisu.insertContainedPort(m, Visualizable.enforce(proc))
      tile.insertSubmodulesPort(m, mem)
      tileVisu.insertContainedPort(m, Visualizable.enforce(mem))
      tile.insertSubmodulesPort(m, ni)
      tileVisu.insertContainedPort(m, Visualizable.enforce(ni))
      proc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
      mem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
      ni.getViewedVertex().addPorts("tileMemory", "tileProcessor", "bus")
      niMesh(i) = ni
      m.connect(
        proc,
        ni,
        "networkInterface",
        "tileProcessor",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        proc,
        "tileProcessor",
        "networkInterface",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        proc,
        mem,
        "defaultMemory",
        "instructionsAndData",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        mem,
        proc,
        "instructionsAndData",
        "defaultMemory",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        mem,
        ni,
        "networkInterface",
        "tileMemory",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        mem,
        "tileMemory",
        "networkInterface",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      GreyBox.enforce(proc).insertContainedPort(m, Visualizable.enforce(scheduler))
      Allocated.enforce(scheduler).insertAllocationHostsPort(m, proc)
      Allocated.enforce(niSchedule).insertAllocationHostsPort(m, ni)
    }
    // and now the bus
    val bus      = InstrumentedCommunicationModule.enforce(m.newVertex("TDMBus"))
    val busSched = StaticCyclicScheduler.enforce(m.newVertex("busSched"))
    bus.setOperatingFrequencyInHertz(666667000L)
    bus.setFlitSizeInBits(128L)
    bus.setMaxConcurrentFlits(8)
    bus.setMaxCyclesPerFlit(8)
    bus.setInitialLatency(0L)
    Allocated.enforce(busSched).insertAllocationHostsPort(m, bus)
    Visualizable.enforce(bus)
    // and now we connect the NIs in the mesh
    for (i <- 0 until 8) {
      bus.getViewedVertex().addPort("ni_" + i)
      m.connect(
        niMesh(i),
        bus,
        "bus",
        "ni_" + i,
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        bus,
        niMesh(i),
        "ni_" + i,
        "bus",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
    }
    m
  }

  // and now we construct the bigger in memory for the same reason
  val large5x6PlatformModel = {
    val m      = ForSyDeSystemGraph()
    var niMesh = Array.fill(5)(Array.fill[InstrumentedCommunicationModule](6)(null))
    // put the proc elements
    for (i <- 0 until 30; row = i % 5; col = (i - row) / 5) {
      val tile     = AbstractStructure.enforce(m.newVertex("tile_" + i))
      val tileVisu = GreyBox.enforce(tile)
      val proc =
        InstrumentedProcessingModule.enforce(Visualizable.enforce(m.newVertex("tile_cpu_" + i)))
      val mem = GenericMemoryModule.enforce(Visualizable.enforce(m.newVertex("tile_mem" + i)))
      val ni =
        InstrumentedCommunicationModule.enforce(Visualizable.enforce(m.newVertex("tile_ni" + i)))
      val router =
        InstrumentedCommunicationModule.enforce(
          Visualizable.enforce(m.newVertex("tile_router" + i))
        )
      val scheduler       = StaticCyclicScheduler.enforce(m.newVertex("tile_os" + i))
      val niScheduler     = StaticCyclicScheduler.enforce(m.newVertex("tile_ni_slots" + i))
      val routerScheduler = StaticCyclicScheduler.enforce(m.newVertex("tile_router_slots" + i))
      proc.setOperatingFrequencyInHertz(50000000L)
      mem.setOperatingFrequencyInHertz(50000000L)
      mem.setSpaceInBits(16000 * 8L)
      ni.setOperatingFrequencyInHertz(50000000L)
      ni.setFlitSizeInBits(128L)
      ni.setMaxConcurrentFlits(6)
      ni.setMaxCyclesPerFlit(6)
      ni.setInitialLatency(0L)
      router.setOperatingFrequencyInHertz(50000000L)
      router.setFlitSizeInBits(128L)
      router.setMaxConcurrentFlits(6)
      router.setMaxCyclesPerFlit(6)
      router.setInitialLatency(0L)
      proc.setModalInstructionsPerCycle(
        Map(
          "eco" -> Map(
            "all" -> (1.0 / 1.2).asInstanceOf[java.lang.Double]
          ).asJava,
          "default" -> Map(
            "all" -> (1.0).asInstanceOf[java.lang.Double]
          ).asJava
        ).asJava
      )
      proc.getViewedVertex().addPorts("networkInterface", "defaultMemory")
      mem.getViewedVertex().addPorts("networkInterface", "instructionsAndData")
      ni.getViewedVertex().addPorts("tileMemory", "tileProcessor", "router")
      router.getViewedVertex().addPorts("ni")
      tile.insertSubmodulesPort(m, proc)
      tileVisu.insertContainedPort(m, Visualizable.enforce(proc))
      tile.insertSubmodulesPort(m, mem)
      tileVisu.insertContainedPort(m, Visualizable.enforce(mem))
      tile.insertSubmodulesPort(m, ni)
      tileVisu.insertContainedPort(m, Visualizable.enforce(ni))
      GreyBox.enforce(proc).insertContainedPort(m, Visualizable.enforce(scheduler))
      Allocated.enforce(scheduler).insertAllocationHostsPort(m, proc)
      Allocated.enforce(niScheduler).insertAllocationHostsPort(m, ni)
      Allocated.enforce(routerScheduler).insertAllocationHostsPort(m, router)
      // connect them
      niMesh(row)(col) = router
      m.connect(
        proc,
        ni,
        "networkInterface",
        "tileProcessor",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        proc,
        "tileProcessor",
        "networkInterface",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        proc,
        mem,
        "defaultMemory",
        "instructionsAndData",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        mem,
        proc,
        "instructionsAndData",
        "defaultMemory",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        mem,
        ni,
        "networkInterface",
        "tileMemory",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        mem,
        "tileMemory",
        "networkInterface",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        router,
        ni,
        "ni",
        "router",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )
      m.connect(
        ni,
        router,
        "router",
        "ni",
        EdgeTrait.PLATFORM_PHYSICALCONNECTION,
        EdgeTrait.VISUALIZATION_VISUALCONNECTION
      )

    }

    // and now we connect the NIs in the mesh
    for (i <- 0 until 30; row = i % 5; col = (i - row) / 5) {
      if (row > 0) {
        val r = row - 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
        niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(r)(col),
          s"to_${r}_${col}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(r)(col),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${r}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
      if (row < 4) {
        val r = row + 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${r}_${col}", s"from_${r}_${col}")
        niMesh(r)(col).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(r)(col),
          s"to_${r}_${col}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(r)(col),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${r}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
      if (col > 0) {
        val c = col - 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
        niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(row)(c),
          s"to_${row}_${c}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(row)(c),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${c}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
      if (col < 5) {
        val c = col + 1;
        niMesh(row)(col).getViewedVertex().addPorts(s"to_${row}_${c}", s"from_${row}_${c}")
        niMesh(row)(c).getViewedVertex().addPorts(s"to_${row}_${col}", s"from_${row}_${col}")
        m.connect(
          niMesh(row)(col),
          niMesh(row)(c),
          s"to_${row}_${c}",
          s"from_${row}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
        m.connect(
          niMesh(row)(c),
          niMesh(row)(col),
          s"to_${row}_${col}",
          s"from_${c}_${col}",
          EdgeTrait.PLATFORM_PHYSICALCONNECTION,
          EdgeTrait.VISUALIZATION_VISUALCONNECTION
        )
      }
    }

    m
  }

  val sobelSDF3    = forSyDeModelHandler.loadModel("models/sdf3/a_sobel.hsdf.xml")
  val susanSDF3    = forSyDeModelHandler.loadModel("models/sdf3/b_susan.hsdf.xml")
  val rastaSDF3    = forSyDeModelHandler.loadModel("models/sdf3/c_rasta.hsdf.xml")
  val jpegEnc1SDF3 = forSyDeModelHandler.loadModel("models/sdf3/d_jpegEnc1.hsdf.xml")
  val g10_3_cyclicSDF3 = forSyDeModelHandler.loadModel("models/sdf3/g10_3_cycl.sdf.xml")
  val allSDFApps =
    sobelSDF3.merge(susanSDF3).merge(rastaSDF3).merge(jpegEnc1SDF3).merge(g10_3_cyclicSDF3)

  val appsAndBusSmall = allSDFApps.merge(busLike8nodePlatformModel)
  val appsAndSmall    = allSDFApps.merge(small2x2PlatformModel)
  val appsAndLarge    = allSDFApps.merge(large5x6PlatformModel)

  test("Created platform models in memory successfully and can write them out") {
    forSyDeModelHandler.writeModel(small2x2PlatformModel, "models/small_platform.fiodl")
    forSyDeModelHandler.writeModel(
      busLike8nodePlatformModel,
      "models/bus_small_platform.fiodl"
    )
    forSyDeModelHandler.writeModel(large5x6PlatformModel, "models/large_platform.fiodl")
    forSyDeModelHandler.writeModel(
      small2x2PlatformModel,
      "models/small_platform_visual.kgt"
    )
    forSyDeModelHandler.writeModel(
      busLike8nodePlatformModel,
      "models/bus_small_platform_visual.kgt"
    )
    forSyDeModelHandler.writeModel(
      large5x6PlatformModel,
      "models/large_platform_visual.kgt"
    )
  }

  test("Correct decision model identification of the Small platform") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(small2x2PlatformModel)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SchedulableTiledMultiCore]).isDefined)
  }

  test("Correct decision model identification of the Small Bus platform") {
    val identified = identificationHandler.identifyDecisionModels(
      Set(ForSyDeDesignModel(busLike8nodePlatformModel))
    )
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SchedulableTiledMultiCore]).isDefined)
  }

  test("Correct decision model identification of the Large platform") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(large5x6PlatformModel)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SchedulableTiledMultiCore]).isDefined)
  }

  test("Correct decision model identification of Sobel") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(sobelSDF3)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val sobelDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(sobelDM.sdfRepetitionVectors.sameElements(Array(1, 1, 1, 1)))
  }

  test("Correct identification and DSE of Sobel to Small") {
    val inputSystem = sobelSDF3.merge(small2x2PlatformModel)
    val designModel = ForSyDeDesignModel(inputSystem)
    val identified =
      identificationHandler.identifyDecisionModels(Set(designModel))
    val chosen = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    val solutions = chosen
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .flatMap(sol => identificationHandler.integrateDecisionModel(designModel, sol))
          .flatMap(sol =>
            sol match {
              case f: ForSyDeDesignModel => Some(f.systemGraph)
              case _                     => Option.empty
            }
          )
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                inputSystem.merge(sol),
                "models/sdf3/results/sobel_and_small_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              inputSystem.merge(sol),
              "models/sdf3/results/sobel_and_small_result_visual.kgt"
            )
            sol
          )
          .take(solutionsTaken)
      )
    assert(solutions.size >= 1)
  }

  test("Correct identification and DSE of Sobel to bus Small") {
    val inputSystem = sobelSDF3.merge(busLike8nodePlatformModel)
    val designModel = ForSyDeDesignModel(inputSystem)
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(inputSystem)))
    val chosen = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    val solutions = chosen
      .take(1)
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .flatMap(sol => identificationHandler.integrateDecisionModel(designModel, sol))
          .flatMap(sol =>
            sol match {
              case f: ForSyDeDesignModel => Some(f.systemGraph)
              case _                     => Option.empty
            }
          )
          .take(solutionsTaken)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                inputSystem.merge(sol),
                "models/sdf3/results/sobel_and_bus_small_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              inputSystem.merge(sol),
              "models/sdf3/results/sobel_and_bus_small_result_visual.kgt"
            )
            sol
          )
      )
    assert(solutions.size >= 1)
  }

  test("Correct identification and DSE of Sobel to Large", ResourceHungry) {
    val inputSystem = sobelSDF3.merge(large5x6PlatformModel)
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(inputSystem)))
    val designModel = ForSyDeDesignModel(inputSystem)
    val chosen      = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    val solutions = chosen
      .take(1)
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .flatMap(sol => identificationHandler.integrateDecisionModel(designModel, sol))
          .flatMap(sol =>
            sol match {
              case f: ForSyDeDesignModel => Some(f.systemGraph)
              case _                     => Option.empty
            }
          )
          .take(solutionsTaken)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                inputSystem.merge(sol),
                "models/sdf3/results/sobel_and_large_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              inputSystem.merge(sol),
              "models/sdf3/results/sobel_and_large_result_visual.kgt"
            )
            sol
          )
      )
    assert(solutions.size >= 1)
  }

  test("Correct decision model identification of SUSAN") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(susanSDF3)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val susanDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(susanDM.sdfRepetitionVectors.sameElements(Array(1, 1, 1, 1, 1)))
  }

  test("Correct decision model identification of RASTA") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(rastaSDF3)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val rastaDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(rastaDM.sdfRepetitionVectors.sameElements(Array(1, 1, 1, 1, 1, 1, 1)))
  }

  test("Correct decision model identification of JPEG") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(jpegEnc1SDF3)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val jpegDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(jpegDM.sdfRepetitionVectors.sameElements(Array.fill(jpegDM.actorsIdentifiers.size)(1)))
  }

  test("Correct decision model identification of Synthetic") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(g10_3_cyclicSDF3)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val syntheticDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assert(
      syntheticDM.sdfRepetitionVectors.sameElements(
        Array.fill(syntheticDM.actorsIdentifiers.size)(1)
      )
    )
  }

  test("Correct identification and DSE of Synthetic to bus Small") {
    val inputSystem = g10_3_cyclicSDF3.merge(busLike8nodePlatformModel)
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(inputSystem)))
    val designModel = ForSyDeDesignModel(inputSystem)
    val chosen      = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    val solutions = chosen
      .take(1)
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .flatMap(sol => identificationHandler.integrateDecisionModel(designModel, sol))
          .flatMap(sol =>
            sol match {
              case f: ForSyDeDesignModel => Some(f.systemGraph)
              case _                     => Option.empty
            }
          )
          .take(solutionsTaken)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                inputSystem.merge(sol),
                "models/sdf3/results/synthetic_and_bus_small_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              inputSystem.merge(sol),
              "models/sdf3/results/synthetic_and_bus_small_result_visual.kgt"
            )
            sol
          )
      )
    assert(solutions.size >= 1)
  }

  test("Correct decision model identification of all Applications together") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(allSDFApps)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFApplication]).isDefined)
    val allSDFAppsDM = identified
      .find(m => m.isInstanceOf[SDFApplication])
      .map(m => m.asInstanceOf[SDFApplication])
      .get
    assertResult(Vector.fill(allSDFAppsDM.actorsIdentifiers.size)(1))(
      allSDFAppsDM.sdfRepetitionVectors
    )
  }

  test("Correct identification and DSE of all and small platform") {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(appsAndSmall)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFToTiledMultiCore]).isDefined)
    val designModel = ForSyDeDesignModel(appsAndSmall)
    val chosen      = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    val solutions = chosen
      .take(1)
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .flatMap(sol => identificationHandler.integrateDecisionModel(designModel, sol))
          .flatMap(sol =>
            sol match {
              case f: ForSyDeDesignModel => Some(f.systemGraph)
              case _                     => Option.empty
            }
          )
          .take(solutionsTaken)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                appsAndSmall.merge(sol),
                "models/sdf3/results/all_and_small_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              appsAndSmall.merge(sol),
              "models/sdf3/results/all_and_small_result_visual.kgt"
            )
            sol
          )
          .take(solutionsTaken)
      )
    assert(solutions.size >= 1)
  }

  test("Correct identification and DSE of all and small bus platform", ResourceHungry) {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(appsAndBusSmall)))
    val designModel = ForSyDeDesignModel(appsAndBusSmall)
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFToTiledMultiCore]).isDefined)
    val chosen = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    val solutions = chosen
      .take(1)
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .flatMap(sol => identificationHandler.integrateDecisionModel(designModel, sol))
          .flatMap(sol =>
            sol match {
              case f: ForSyDeDesignModel => Some(f.systemGraph)
              case _                     => Option.empty
            }
          )
          .take(solutionsTaken)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                appsAndBusSmall.merge(sol),
                "models/sdf3/results/all_and_bus_small_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              appsAndBusSmall.merge(sol),
              "models/sdf3/results/all_and_bus_small_result_visual.kgt"
            )
            sol
          )
          .take(solutionsTaken)
      )
    assert(solutions.size >= 1)
  }

  test("Correct identification and DSE of all and large platform", ResourceHungry) {
    val identified =
      identificationHandler.identifyDecisionModels(Set(ForSyDeDesignModel(appsAndLarge)))
    assert(identified.size > 0)
    assert(identified.find(m => m.isInstanceOf[SDFToTiledMultiCore]).isDefined)
    val designModel = ForSyDeDesignModel(appsAndLarge)
    val chosen      = explorationHandler.chooseExplorersAndModels(identified)
    assert(chosen.size > 0)
    val solutions = chosen
      .take(1)
      .flatMap((explorer, decisionModel) =>
        explorer
          .explore(decisionModel)
          .flatMap(sol => identificationHandler.integrateDecisionModel(designModel, sol))
          .flatMap(sol =>
            sol match {
              case f: ForSyDeDesignModel => Some(f.systemGraph)
              case _                     => Option.empty
            }
          )
          .take(solutionsTaken)
          .map(sol =>
            forSyDeModelHandler
              .writeModel(
                appsAndLarge.merge(sol),
                "models/sdf3/results/all_and_large_result.fiodl"
              )
            forSyDeModelHandler.writeModel(
              appsAndLarge.merge(sol),
              "models/sdf3/results/all_and_large_result_visual.kgt"
            )
            sol
          )
          .take(solutionsTaken)
      )
    assert(solutions.size >= 1)
  }

}
