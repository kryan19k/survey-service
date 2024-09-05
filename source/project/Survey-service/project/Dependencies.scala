import sbt._

object Dependencies {
  object V {
    val tessellation = "2.8.0" 
    val http4s = "0.23.18"
    val circe = "0.14.5"
    val decline = "2.4.1"
    val doobie = "1.0.0-RC2"
    val postgres = "42.5.4"
    val weaver = "0.8.3"
    val catsEffect = "3.5.1"
  }

  def tessellation(artifact: String): ModuleID = "org.constellation" %% s"tessellation-$artifact" % V.tessellation

  object Libraries {
    val tessellationNodeShared = tessellation("node-shared")
    val tessellationCurrencyL0 = tessellation("currency-l0")
    val tessellationCurrencyL1 = tessellation("currency-l1")
    

    val http4sCore = "org.http4s" %% "http4s-core" % V.http4s
    val http4sDsl = "org.http4s" %% "http4s-dsl" % V.http4s
    val http4sServer = "org.http4s" %% "http4s-server" % V.http4s
    val http4sClient = "org.http4s" %% "http4s-client" % V.http4s
    val http4sCirce = "org.http4s" %% "http4s-circe" % V.http4s

    val circeCore = "io.circe" %% "circe-core" % V.circe
    val circeGeneric = "io.circe" %% "circe-generic" % V.circe
    val circeParser = "io.circe" %% "circe-parser" % V.circe

    val declineCore = "com.monovore" %% "decline" % V.decline
    val declineEffect = "com.monovore" %% "decline-effect" % V.decline
    val declineRefined = "com.monovore" %% "decline-refined" % V.decline

    val doobieCore = "org.tpolecat" %% "doobie-core" % V.doobie
    val doobieHikari = "org.tpolecat" %% "doobie-hikari" % V.doobie
    val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % V.doobie

    val postgres = "org.postgresql" % "postgresql" % V.postgres

    val bouncyCastle = "org.bouncycastle" % "bcprov-jdk15on" % "1.70"

    val refined = "eu.timepit" %% "refined" % "0.10.1"

    // Test libraries
    val weaverCats = "com.disneystreaming" %% "weaver-cats" % V.weaver
    val weaverDiscipline = "com.disneystreaming" %% "weaver-discipline" % V.weaver
    val weaverScalaCheck = "com.disneystreaming" %% "weaver-scalacheck" % V.weaver
    val catsEffectTestkit = "org.typelevel" %% "cats-effect-testkit" % V.catsEffect
  }

  object CompilerPlugin {
    val kindProjector = compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)
    val betterMonadicFor = compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
    val semanticDB = compilerPlugin("org.scalameta" % "semanticdb-scalac" % "4.7.1" cross CrossVersion.full)
  }

  // Scalafix rules
  val organizeImports = "com.github.liancheng" %% "organize-imports" % "0.5.0"
}