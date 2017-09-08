import sbt._

object Dependencies {
  lazy val postgresDependency = "org.postgresql" % "postgresql" % "42.1.1"
  lazy val slickDependencies = Seq(
    "com.typesafe.slick" %% "slick" % "3.2.1",
    "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1",
    "com.typesafe.slick" %% "slick-testkit" % "3.2.1" % Test,
    postgresDependency,
    "com.github.tminglei" %% "slick-pg" % "0.15.0"
  )
  lazy val slickCodegenDependency = "com.typesafe.slick" %% "slick-codegen" % "3.2.1"
}
