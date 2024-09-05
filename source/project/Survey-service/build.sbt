import Dependencies.*
import sbt.*

ThisBuild / organization := "com.my.survey"
ThisBuild / scalaVersion := "2.13.10"
ThisBuild / evictionErrorLevel := Level.Warn

ThisBuild / assemblyMergeStrategy := {
  case "logback.xml" => MergeStrategy.first
  case x if x.contains("io.netty.versions.properties") => MergeStrategy.discard
  case PathList(xs@_*) if xs.last == "module-info.class" => MergeStrategy.first
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}

ThisBuild / resolvers ++= Seq(
  "Constellation Releases" at "https://maven.pkg.github.com/Constellation-Labs/tessellation",
  Resolver.mavenLocal,
  Resolver.githubPackages("abankowski", "http-request-signer"),
  "jitpack" at "https://jitpack.io"
)

ThisBuild / credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "kryan19k",
  sys.env.getOrElse("GITHUB_TOKEN", "")
)

lazy val root = (project in file("."))
  .settings(
    name := "survey-metagraph"
  )
  .aggregate(sharedData, l0, l1, dataL1, currencyL1)
  
lazy val sharedData = (project in file("modules/shared_data"))
  .enablePlugins(AshScriptPlugin, BuildInfoPlugin, JavaAppPackaging)
  .settings(
    name := "survey-shared_data",
    scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info", "-language:reflectiveCalls"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.my.survey.shared_data",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      CompilerPlugin.kindProjector,
      CompilerPlugin.betterMonadicFor,
      CompilerPlugin.semanticDB,
      Libraries.tessellationNodeShared
    )
  )

lazy val l0 = (project in file("modules/l0"))
  .enablePlugins(AshScriptPlugin, BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(sharedData)
  .settings(
    name := "survey-currency-l0",
    scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info", "-language:reflectiveCalls"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.my.survey.currency_l0",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      CompilerPlugin.kindProjector,
      CompilerPlugin.betterMonadicFor,
      CompilerPlugin.semanticDB,
      Libraries.declineRefined,
      Libraries.declineCore,
      Libraries.declineEffect,
      Libraries.tessellationCurrencyL0
    )
  )

lazy val l1 = (project in file("modules/l1"))
  .enablePlugins(AshScriptPlugin, BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(sharedData)
  .settings(
    name := "survey-l1",
    scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info", "-language:reflectiveCalls"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.my.survey.l1",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      CompilerPlugin.kindProjector,
      CompilerPlugin.betterMonadicFor,
      CompilerPlugin.semanticDB,
      Libraries.tessellationCurrencyL1
    ),
    // Set a unique target directory for l1
    target := file("target/l1")
  )

lazy val dataL1 = (project in file("modules/data_l1"))
  .enablePlugins(AshScriptPlugin, BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(sharedData)
  .settings(
    name := "survey-data_l1",
    scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info", "-language:reflectiveCalls"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.my.survey.data_l1",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      CompilerPlugin.kindProjector,
      CompilerPlugin.betterMonadicFor,
      CompilerPlugin.semanticDB,
      Libraries.tessellationCurrencyL1
    ),
    // Set a unique target directory for dataL1
    target := file("target/data_l1")
  )

lazy val currencyL1 = (project in file("modules/currency_l1"))
  .enablePlugins(AshScriptPlugin, BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(sharedData)
  .settings(
    name := "survey-currency-l1",
    scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info", "-language:reflectiveCalls"),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.my.survey.currency_l1",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      CompilerPlugin.kindProjector,
      CompilerPlugin.betterMonadicFor,
      CompilerPlugin.semanticDB,
      Libraries.tessellationCurrencyL1
    ),
    // Set a unique target directory for currencyL1
    target := file("target/currency_l1")
  )

// Update the root project to include currencyL1
lazy val root = (project in file("."))
  .settings(
    name := "survey-metagraph"
  )
  .aggregate(sharedData, l0, l1, dataL1, currencyL1)