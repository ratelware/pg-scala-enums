package extensions.database.pgsql

import java.nio.file.{Files, Path, Paths}
import java.sql.DriverManager
import java.util

import scala.collection.mutable.HashMap

object PgProfileGenerator {

  case class SourceCode(s: String) {
    override def toString: String = s
  }

  def main(args: Array[String]): Unit = {
    val dbPath = args(0)
    val targetDirectory = args(1)
    val packageName = args(2) +".profile"
    val targetFile = Paths.get(targetDirectory, packageName.replaceAllLiterally(".", "/"), "ExtendedPostgresProfile.scala")
    println("Generating code for extended Postgres profile (with enums and stuff)")

    val allEnums = database.model.enums.Enums.allEnums.map(e => toSingleEnumImplicits(e._1, e._2)).toSeq

    writeToFile(targetFile, makeSourceFileContent(packageName, allEnums))
    println("Profile generation completed successfully!")
  }

  def toSingleEnumImplicits(originalName: String, enumClass: Enumeration): SourceCode = {
    val className = NameConverter.enumNameToScalaClassName(enumClass.getClass.getSimpleName)
    SourceCode(
      s"""
         |    // mapping for $originalName / $className
         |    implicit val ${className}TypeMapper = createEnumJdbcType("$originalName", $className)
         |    implicit val ${className}ListTypeMapper = createEnumListJdbcType("${NameConverter.enumNameToListName(originalName)}", $className)
         |    implicit val ${className}ColumnExtensionMethodsBuilder = createEnumColumnExtensionMethodsBuilder($className)
         |    implicit val ${className}OptionColumnExtensionMethodsBuilder = createEnumOptionColumnExtensionMethodsBuilder($className)
         """.stripMargin)
  }

  def makeSourceFileContent(pkg: String, items: Seq[SourceCode]): SourceCode = {
SourceCode(s"""
package $pkg

// AUTO-GENERATED FILE, DO NOT MODIFY
import com.github.tminglei.slickpg._
import database.model.enums.Enums._
import slick.basic.Capability

trait ExtendedPostgresProfile extends ExPostgresProfile
  with PgArraySupport
  with PgDate2Support
  with PgRangeSupport
  with PgHStoreSupport
  with PgSearchSupport
  with PgNetSupport
  with PgLTreeSupport
  with PgEnumSupport
{
  def pgjson = "jsonb" // jsonb support is in postgres 9.4.0 onward; for 9.3.x use "json"

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
  super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate

  override val api = ExtendedAPI
  object ExtendedAPI extends API with ArrayImplicits
    with DateTimeImplicits
    with NetImplicits
    with LTreeImplicits
    with RangeImplicits
    with HStoreImplicits
    with SearchImplicits
    with SearchAssistants
  {
    implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)

    ${items.mkString("\n")}
  }
}

object ExtendedPostgresProfile extends ExtendedPostgresProfile
""".stripMargin)
  }

  def writeToFile(f: Path, str: SourceCode): Unit = {
    Files.createDirectories(f.getParent)
    Files.write(f, str.s.getBytes)
  }
}
