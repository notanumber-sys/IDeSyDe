package idesyde.cli

import java.io.File
import java.nio.file.Paths
// import scribe.format.FormatterInterpolator
// import scribe.Level
import idesyde.IDeSyDeStandalone
import idesyde.utils.Logger

class IDeSyDeCLIParser(using logger: Logger)
    extends scopt.OptionParser[IDeSyDeRunConfig]("idesyde"):
  head(
    """
          ___  ___        ___        ___
         |_ _||   \  ___ / __| _  _ |   \  ___ 
          | | | |) |/ -_)\__ \| || || |) |/ -_)
         |___||___/ \___||___/ \_, ||___/ \___|
                               |__/

        Automated Identification and Exploration of Design Spaces in ForSyDe
        """
  )
  arg[File]("<inputModel> [<inputModel> ...]")
    .unbounded()
    .action((f, x) => x.copy(inputModelsPaths = x.inputModelsPaths.appended(f.toPath)))

  opt[File]('o', "out")
    .text(
      "If the output is an existing directory, write all solutions to the directory. Otherwise, the lastest solution is written to the destination."
    )
    .valueName("<outputModel>")
    .action((f, x) => x.copy(outputModelPath = f.toPath))

  opt[File]("log")
    .text("Writes the output to this path aside from the STDOUT.")
    .valueName("<logFile>")
    .action((f, x) => {
      IDeSyDeStandalone.additionalLogFiles.append(f)
      x
    })

  opt[String]("decision-model")
    .text(
      "Filters the allowed decision models to be chosen after identification. All identified are chosen if none is specified."
    )
    .valueName("<DecisionModelID>")
    .action((f, x) => x.copy(allowedDecisionModels = x.allowedDecisionModels.appended(f)))

  opt[String]('v', "verbosity")
    .valueName("<verbosityLevel>")
    .action((v, x) => {
      IDeSyDeStandalone.loggingLevel = v
      x
    })
    .text(
      "Sets the logging level. The options are, in increasing verbosity order: ERROR, WARN, INFO, DEBUG. Default: INFO."
    )

  opt[Int]("solutions-limit")
    .valueName("<solutionsLimits>")
    .action((v, x) => x.copy(solutionLimiter = v))
    .text(
      "Sets the maximum number of outputted feasible solutions, when (pareto) optimality cannot be proved before the given limit. All solutions until optimality are returned if nothing is given."
    )

  opt[Long]("exploration-timeout")
    .valueName("<explorationTimeOut>")
    .action((v, x) => x.copy(explorationTimeOutInSecs = v))
    .text(
      "Sets the maximum number of times the exploration can last. If the number is non-positive, there is not timeout. Default is 0."
    )

  opt[Long]("time-multiplier")
      .valueName("<timeMultiplier>")
      .action((v, x) => x.copy(timeMultiplier = Some(v)))
      .text("Specifies a scaling before integer discretization on all time parameters. Default is dynamic behaviour.")

  opt[Long]("memory-divider")
      .valueName("<memoryDivider>")
      .action((v, x) => x.copy(memoryDivider = Some(v)))
      .text("Specifies a scaling before memory discretization on all memory parameters. Default is dynamic behavior.")

end IDeSyDeCLIParser
