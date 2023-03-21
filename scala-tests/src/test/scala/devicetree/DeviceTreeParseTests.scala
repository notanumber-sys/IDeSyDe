package devicetree

import org.scalatest.funsuite.AnyFunSuite
import mixins.LoggingMixin
import mixins.HasShortcuts
import idesyde.devicetree.identification.CanParseDeviceTree
import idesyde.devicetree.identification.DeviceTreeDesignModel
import idesyde.identification.common.models.platform.SharedMemoryMultiCore

class DeviceTreeParseTests
    extends AnyFunSuite
    with LoggingMixin
    with HasShortcuts
    with CanParseDeviceTree {

  val inlinedCode = """
    chassis-type = "embedded"
    compatible = "riscv"
    bus-frequency = <50000000>
    bus-concurrency = <1>
    bus-flit = <32>
    bus-clock-per-flit = <32>
    cpus {
      cpu@0 {
        clock-frequency = <50000000>

        ops-per-cycle {
          default {
            f64add = <1 6>
            f64mul = <1 1000>
          }
        }
      }
    }

    memory@0x00000 {
        clock-frequency = <50000000>
        device-type = "memory"
        reg = <0x0 0x800000>
    }
    """

  test("Trying to parse the most basic ones") {
    val root = parseDeviceTree(inlinedCode)
    root match
      case Success(result, next) =>
        val model = DeviceTreeDesignModel(List(result))
        // println(result)
        // result.cpus.foreach(cpu => println(cpu.operationsProvided))
        // result.memories.foreach(mem => println(mem.memorySize))
        val identified = identify(model)
        assert(identified.exists(_.isInstanceOf[SharedMemoryMultiCore]))
      case Failure(msg, next) => println(msg)
      case Error(msg, next)   => println(msg)
  }
}
