ThisBuild / organization := "io.github.forsyde"
ThisBuild / version := "0.3.4"
ThisBuild / scalaVersion := "3.1.3"

// ThisBuild / resolvers += Resolver.mavenLocal

lazy val forsydeIoVersion  = "0.5.17"
lazy val jgraphtVersion    = "1.5.1"
lazy val scribeVersion     = "3.10.2"
lazy val breezeVersion     = "2.1.0"
lazy val scalaGraphVersion = "1.13.5"

lazy val root = project
  .in(file("."))
  .aggregate(common, cli, choco, forsyde, minizinc)

lazy val core = (project in file("scala-core"))

lazy val common = (project in file("scala-common"))
  .dependsOn(core)
  .settings(
    libraryDependencies ++= Seq(
      ("org.scala-graph" %% "graph-core"   % scalaGraphVersion).cross(CrossVersion.for3Use2_13),
      "org.jgrapht"       % "jgrapht-core" % jgraphtVersion,
      "org.jgrapht"       % "jgrapht-opt"  % jgraphtVersion,
      "org.scalanlp"     %% "breeze"       % breezeVersion,
      "com.outr"         %% "scribe"       % scribeVersion
    )
  )

lazy val forsyde = (project in file("scala-forsyde"))
  .dependsOn(core)
  .dependsOn(common)
  .settings(
    libraryDependencies ++= Seq(
      ("org.scala-graph" %% "graph-core" % scalaGraphVersion).cross(CrossVersion.for3Use2_13),
      "io.github.forsyde" % "forsyde-io-java-core" % forsydeIoVersion,
      "org.jgrapht"       % "jgrapht-core"         % jgraphtVersion,
      "org.jgrapht"       % "jgrapht-opt"          % jgraphtVersion,
      "org.typelevel"    %% "spire"                % "0.18.0"
    )
  )

lazy val minizinc = (project in file("scala-minizinc"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(forsyde)
  .settings(
    libraryDependencies ++= Seq(
      "com.outr"     %% "scribe"       % scribeVersion,
      "com.lihaoyi"  %% "upickle"      % "1.4.0",
      "org.jgrapht"   % "jgrapht-core" % jgraphtVersion,
      "org.jgrapht"   % "jgrapht-opt"  % jgraphtVersion,
      "org.scalanlp" %% "breeze"       % breezeVersion
    )
  )

lazy val choco = (project in file("scala-choco"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(forsyde)
  .settings(
    libraryDependencies ++= Seq(
      "com.novocode"     % "junit-interface" % "0.11" % "test",
      "org.choco-solver" % "choco-solver"    % "4.10.9",
      "org.jgrapht"      % "jgrapht-core"    % jgraphtVersion,
      "org.jgrapht"      % "jgrapht-opt"     % jgraphtVersion,
      "com.outr"        %% "scribe"          % scribeVersion
    )
  )

lazy val cli = (project in file("scala-cli"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(choco)
  .dependsOn(forsyde)
  .dependsOn(minizinc)
  // .enablePlugins(ScalaNativePlugin)
  .enablePlugins(UniversalPlugin, JavaAppPackaging) //, JlinkPlugin)
  .settings(
    Compile / mainClass := Some("idesyde.IDeSyDeStandalone"),
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt"       % "4.0.1",
      "com.outr"         %% "scribe"      % scribeVersion,
      "com.outr"         %% "scribe-file" % scribeVersion
    ),
    maintainer := "jordao@kth.se"
    // taken and adapted from https://www.scala-sbt.org/sbt-native-packager/archetypes/jlink_plugin.html
    // jlinkModulePath := {
    //   val paths = (jlinkBuildImage / fullClasspath).value
    //   paths.filter(f => {
    //     f.get(moduleID.key).exists(mID => mID.name.contains("jheaps")) ||
    //     f.get(moduleID.key).exists(mID => mID.name.contains("commons-text"))
    //   }).map(_.data)
    // }
  )

lazy val tests = (project in file("scala-tests"))
  .dependsOn(core)
  .dependsOn(common)
  .dependsOn(choco)
  .dependsOn(forsyde)
  .dependsOn(minizinc)
  .dependsOn(cli)
  .settings(
    libraryDependencies ++= Seq(
      ("org.scala-graph" %% "graph-core"   % scalaGraphVersion).cross(CrossVersion.for3Use2_13),
      "org.scalatest"    %% "scalatest"                % "3.2.12" % "test",
      "org.scalatest"    %% "scalatest-funsuite"       % "3.2.12" % "test",
      "io.github.forsyde" % "forsyde-io-java-core"     % forsydeIoVersion,
      "io.github.forsyde" % "forsyde-io-java-amalthea" % forsydeIoVersion,
      "io.github.forsyde" % "forsyde-io-java-sdf3"     % forsydeIoVersion,
      "io.github.forsyde" % "forsyde-io-java-graphviz" % forsydeIoVersion
    ),
    Test / parallelExecution := false
  )

// TODO: figure out what is
ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}

// /Compile / resourceDirectory := baseDirectory.value / "resources"
