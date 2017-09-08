import Dependencies._

scalaVersion := "2.12.3"

lazy val commonSettings = Seq(
  scalaVersion := "2.12.3"
)

val dbUrl = "jdbc:postgresql://localhost:5432/dbName" // connection info for a pre-populated throw-away, in-memory db for this demo, which is freshly initialized on every run
val pkg = "database.model"

lazy val generateEnums = TaskKey[Seq[File]]("gen-enums")
lazy val pgEnum = project
  .settings(commonSettings)
  .settings(libraryDependencies += postgresDependency)

lazy val generateEnumsTask = Def.task {
  val dir = (sourceManaged in pgEnum).value
  val cp = (fullClasspath in Compile in pgEnum).value
  val r = (runner in Compile in pgEnum).value
  val s = streams.value
  val outputDir = (dir / "main").getPath // place generated files in sbt's managed sources folder
  toError(r.run("extensions.database.pgsql.EnumGenerator", cp.files, Array(dbUrl, outputDir, pkg), s.log))
  val fname = outputDir + "/database/model/enums/Enums.scala"
  Seq(file(fname))
}
generateEnums := { generateEnumsTask.value }

lazy val generatePgProfile = TaskKey[Seq[File]]("gen-pg-profile")
lazy val pgProfile = project
  .settings(commonSettings)
  .settings(libraryDependencies ++= slickDependencies)
  .settings(libraryDependencies += slickCodegenDependency)
  .settings(sourceGenerators in Compile += generateEnumsTask.taskValue)
  .dependsOn(pgEnum)

lazy val generatePgProfileTask = Def.task {
  val dir = (sourceManaged in pgProfile).value
  val cp = (fullClasspath in Compile in pgProfile).value
  val r = (runner in Compile in pgProfile).value
  val s = streams.value
  val outputDir = (dir / "main").getPath // place generated files in sbt's managed sources folder
  toError(r.run("extensions.database.pgsql.PgProfileGenerator", cp.files, Array(dbUrl, outputDir, pkg), s.log))
  val fname = outputDir + "/database/model/profile/ExtendedPostgresProfile.scala"
  Seq(file(fname))
}
generatePgProfile := { generatePgProfileTask.value }

lazy val generateTables = TaskKey[Seq[File]]("gen-tables")
lazy val pgTables = project
  .settings(commonSettings)
  .settings(libraryDependencies ++= slickDependencies)
  .settings(libraryDependencies += slickCodegenDependency)
  .settings(sourceGenerators in Compile += generatePgProfileTask.taskValue)
  .dependsOn(pgProfile, pgEnum)

lazy val generateTablesTask = Def.task {
  val dir = (sourceManaged in pgTables).value
  val cp = (fullClasspath in Compile in pgTables).value
  val r = (runner in Compile in pgTables).value
  val s = streams.value
  val outputDir = (dir / "main").getPath // place generated files in sbt's managed sources folder
  val fname = new File(s"$outputDir/database/model/tables/Tables.scala")
  if(!fname.exists()) {
    toError(r.run("extensions.database.pgsql.TableGenerator", cp.files, Array(dbUrl, outputDir, pkg), s.log))
  }
  Seq(file(fname.getAbsolutePath))
}
generateTables := { generateTablesTask.value }

