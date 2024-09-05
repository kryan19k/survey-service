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

lazy val commonSettings = Seq(
  scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info", "-language:reflectiveCalls"),
  libraryDependencies ++= Seq(
    CompilerPlugin.kindProjector,
    CompilerPlugin.betterMonadicFor,
    CompilerPlugin.semanticDB
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "survey-metagraph"
  )
  .aggregate(sharedData, currencyL0, currencyL1, dataL1)

lazy val sharedData = (project in file("modules/shared_data"))
  .enablePlugins(AshScriptPlugin, BuildInfoPlugin, JavaAppPackaging)
  .settings(commonSettings)
  .settings(
    name := "survey-shared_data",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.my.survey.shared_data",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      Libraries.tessellationNodeShared,
      Libraries.circeCore,
      Libraries.circeGeneric,
      Libraries.circeParser,
      Libraries.refined
      
    )
  )

lazy val currencyL0 = (project in file("modules/l0"))
  .enablePlugins(AshScriptPlugin, BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(sharedData)
  .settings(commonSettings)
  .settings(
    name := "survey-currency-l0",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.my.survey.l0",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      Libraries.declineRefined,
      Libraries.declineCore,
      Libraries.declineEffect,
      Libraries.tessellationNodeShared,
      Libraries.tessellationCurrencyL0,
      Libraries.http4sCore,
      Libraries.http4sDsl,
      Libraries.http4sServer,
      Libraries.http4sClient,
      Libraries.http4sCirce
    )
  )

lazy val currencyL1 = (project in file("modules/l1"))
  .enablePlugins(AshScriptPlugin, BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(sharedData)
  .settings(commonSettings)
  .settings(
    name := "survey-currency-l1",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.my.survey.l1",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      Libraries.tessellationCurrencyL1
    ),
    // Set a unique target directory for l1
    target := file("target/l1")
  )

lazy val dataL1 = (project in file("modules/data_l1"))
  .enablePlugins(AshScriptPlugin, BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(sharedData)
  .settings(commonSettings)
  .settings(
    name := "survey-data_l1",
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.my.survey.data_l1",
    Defaults.itSettings,
    libraryDependencies ++= Seq(
      Libraries.tessellationCurrencyL1
    ),
    // Set a unique target directory for data_l1
    target := file("target/data_l1")
  )


