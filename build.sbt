ThisBuild / scalaVersion := "3.2.2"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / maintainer := "jordao@kth.se"
ThisBuild / organization := "io.forsyde.github"
ThisBuild / publishMavenStyle := true
ThisBuild / publishTo := Some(Opts.resolver.sonatypeStaging)

lazy val forsydeIoVersion = "0.6.3"
lazy val jgraphtVersion   = "1.5.1"
lazy val scribeVersion    = "3.10.2"
// lazy val breezeVersion                 = "2.1.0"
lazy val scalaGraphVersion             = "1.13.5"
lazy val scalaParserCombinatorsVersion = "2.2.0"
lazy val spireVersion                  = "0.18.0"
lazy val upickleVersion                = "3.0.0"
lazy val chocoSolverVersion            = "4.10.10"
lazy val osLibVersion                  = "0.9.1"
lazy val scalaYamlVersion              = "0.0.6"
lazy val flatBufferVersion             = "23.3.3"

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .enablePlugins(SiteScaladocPlugin)
  .enablePlugins(SitePreviewPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .settings(
    ScalaUnidoc / siteSubdirName := "api",
    addMappingsToSiteDir(ScalaUnidoc / packageDoc / mappings, ScalaUnidoc / siteSubdirName),
    paradoxProperties ++= Map(
      "scaladoc.base_url" -> "IDeSyDe/api",
      "github.base_url"   -> "https://github.com/forsyde/IDeSyDe"
    ),
    paradoxRoots := List("index.html")
  )
  .aggregate(common, cli, choco, forsyde, minizinc, matlab, devicetree)

lazy val core = (project in file("scala-core")).settings(
  name := "idesyde-scala-core",
  libraryDependencies ++= Seq("com.lihaoyi" %% "upickle" % upickleVersion)
)

lazy val common = (project in file("scala-common"))
  .dependsOn(core)
  .settings(
    name := "idesyde-scala-common",
    libraryDependencies ++= Seq(
      ("org.scala-graph" %% "graph-core" % scalaGraphVersion).cross(CrossVersion.for3Use2_13),
      "org.typelevel"    %% "spire"      % spireVersion
    ),
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0")
    )
  )

lazy val forsyde = (project in file("scala-forsyde"))
  .dependsOn(core)
  .dependsOn(common)
  .settings(
    name := "idesyde-scala-forsydeio",
    libraryDependencies ++= Seq(
      "io.github.forsyde" % "forsyde-io-java-core" % forsydeIoVersion
    ),
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
    )
  )

lazy val minizinc = (project in file("scala-minizinc"))
  .dependsOn(core)
  .dependsOn(common)
  .settings(
    name := "idesyde-scala-minizinc",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % upickleVersion
    ),
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
    )
  )

lazy val choco = (project in file("scala-choco"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(forsyde)
  .settings(
    name := "idesyde-scala-choco",
    libraryDependencies ++= Seq(
      "com.novocode"     % "junit-interface" % "0.11" % "test",
      "org.choco-solver" % "choco-solver"    % chocoSolverVersion,
      "org.jgrapht"      % "jgrapht-core"    % jgraphtVersion
    ),
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
    )
  )

lazy val matlab = (project in file("scala-bridge-matlab"))
  .dependsOn(core)
  .dependsOn(common)
  .settings(
    name := "idesyde-scala-bridge-matlab"
  )

lazy val devicetree = (project in file("scala-bridge-device-tree"))
  .dependsOn(core)
  .dependsOn(common)
  .settings(
    name := "idesyde-scala-bridge-devicetree",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %%% "scala-parser-combinators" % scalaParserCombinatorsVersion,
      "com.lihaoyi"             %% "os-lib"                   % osLibVersion,
      "org.virtuslab"           %% "scala-yaml"               % scalaYamlVersion
    )
  )

lazy val cli = (project in file("scala-cli"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(choco)
  .dependsOn(forsyde)
  .dependsOn(minizinc)
  .dependsOn(matlab)
  .dependsOn(devicetree)
  // .enablePlugins(ScalaNativePlugin)
  .enablePlugins(UniversalPlugin, JavaAppPackaging, JlinkPlugin)
  .enablePlugins(GraalVMNativeImagePlugin)
  .settings(
    publishArtifact := false,
    licenses := Seq(
      "MIT"  -> url("https://opensource.org/license/mit/"),
      "APL2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
      "EPL2" -> url("https://www.eclipse.org/legal/epl-2.0/")
    ),
    Compile / mainClass := Some("idesyde.IDeSyDeStandalone"),
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt"  % "4.0.1",
      "com.lihaoyi"      %% "os-lib" % osLibVersion
      // "com.outr"         %% "scribe"      % scribeVersion,
      // "com.outr"         %% "scribe-file" % scribeVersion
    ),
    // taken and adapted from https://www.scala-sbt.org/sbt-native-packager/archetypes/jlink_plugin.html
    jlinkModulePath := {
      val paths = (jlinkBuildImage / fullClasspath).value
      paths
        .filter(f => {
          f.get(moduleID.key).exists(mID => mID.name.contains("jheaps")) ||
          // f.get(moduleID.key).exists(mID => mID.name.contains("fastutil")) ||
          // f.get(moduleID.key).exists(mID => mID.name.contains("commons-text")) ||
          f.get(moduleID.key).exists(mID => mID.name.contains("antlr4")) ||
          f.get(moduleID.key).exists(mID => mID.name.contains("automaton")) ||
          f.get(moduleID.key).exists(mID => mID.name.contains("xchart")) ||
          f.get(moduleID.key).exists(mID => mID.name.contains("trove4j"))
        })
        .map(_.data)
    },
    graalVMNativeImageOptions := Seq("--no-fallback", "-H:+ReportExceptionStackTraces"),
    // TODO: This MUST be taken out of here eventually
    jlinkIgnoreMissingDependency := JlinkIgnore.everything
  )

lazy val tests = (project in file("scala-tests"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(choco)
  .dependsOn(forsyde)
  .dependsOn(minizinc)
  .dependsOn(matlab)
  .dependsOn(cli)
  .dependsOn(devicetree)
  .settings(
    publishArtifact := false,
    libraryDependencies ++= Seq(
      ("org.scala-graph" %% "graph-core" % scalaGraphVersion).cross(CrossVersion.for3Use2_13),
      "org.scalatest"    %% "scalatest"  % "3.2.12" % "test",
      "org.scalatest"    %% "scalatest-funsuite"       % "3.2.12" % "test",
      "io.github.forsyde" % "forsyde-io-java-core"     % forsydeIoVersion,
      "io.github.forsyde" % "forsyde-io-java-amalthea" % forsydeIoVersion,
      "io.github.forsyde" % "forsyde-io-java-sdf3"     % forsydeIoVersion,
      "io.github.forsyde" % "forsyde-io-java-graphviz" % forsydeIoVersion,
      "com.outr"         %% "scribe"                   % scribeVersion,
      "org.virtuslab"    %% "scala-yaml"               % scalaYamlVersion
    ),
    Test / parallelExecution := false
  )

// TODO: figure out what is
ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

// /Compile / resourceDirectory := baseDirectory.value / "resources"
lazy val publishDocumentation =
  taskKey[Unit]("Copy the generated documentation to the correct folder")
publishDocumentation := IO.copyDirectory(
  (root / makeSite).value,
  new java.io.File("docs"),
  true,
  false,
  false
)
