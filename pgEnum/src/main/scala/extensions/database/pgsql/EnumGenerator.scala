package extensions.database.pgsql

import java.nio.file.{Files, Path, Paths}
import java.sql.DriverManager
import java.util

import scala.collection.mutable.HashMap

object EnumGenerator {

  case class SourceCode(s: String) {
    override def toString: String = s
  }

  def main(args: Array[String]): Unit = {
    val dbPath = args(0)
    val targetDirectory = args(1)
    val packageName = args(2) +".enums"
    val targetFile = Paths.get(targetDirectory, packageName.replaceAllLiterally(".", "/"), "Enums.scala")
    println("Generating code for SQL enum mapping")
    println(s"Fetching data from DB at ${dbPath} to ${targetFile}(package: ${packageName})")

    val query = "select t.typname, e.enumlabel, e.enumsortorder from pg_enum e " +
      "inner join pg_type t on t.oid=e.enumtypid"

    Class.forName("org.postgresql.Driver")
    val connection = DriverManager.getConnection(dbPath)
    val result = connection.createStatement().executeQuery(query)

    val enumNameToValueAndOrder = HashMap.empty[String, Seq[(String, Int)]]
    while(result.next()) {
      val name = result.getString("typname")
      val label = result.getString("enumlabel")
      val order = result.getInt("enumsortorder")

      enumNameToValueAndOrder += (name -> enumNameToValueAndOrder.getOrElse(name, Seq()).+:((label, order)))
    }

    val enumsCode = enumNameToValueAndOrder.mapValues(_.sortBy(_._2).map(_._1))
      .toSeq.map(en => {
      val label = NameConverter.snakeToCamel(NameConverter.removeTypePostfix(en._1))
      (en._1, label, formatEnumeration(label, en._2))
    })

    val enums = enumsCode.map(_._3)
    val allEnums = defineAllEnumsMap(enumsCode.map(e => (e._1, e._2)))

    writeToFile(targetFile, makeSourceFileContent(packageName, enums.:+(allEnums)))
    println("Enumeration generation completed successfully!")
  }

  def defineAllEnumsMap(enums: Seq[(String, String)]): SourceCode = {
    SourceCode(s"""  val allEnums: Map[String, Enumeration] = Map(
    ${enums.map(e => toSingleEnumListElement(e._1, e._2)).mkString(",\n    ")}
  )""".stripMargin)
  }

  def toSingleEnumListElement(originalName: String, className: String): SourceCode = {
    SourceCode(s""""$originalName" -> $className""")
  }

  def formatEnumeration(label: String, values: Seq[String]): SourceCode = {
SourceCode(s"""  object $label extends Enumeration {
    type $label = Value
    ${values.map(toSingleEnumerationDefinition).mkString("\n    ")}
  }
""".stripMargin)
  }

  def toSingleEnumerationDefinition(v: String): SourceCode = {
    SourceCode(s"""val ${NameConverter.snakeToCamel(v)} = Value("$v")""")
  }

  def makeSourceFileContent(pkg: String, items: Seq[SourceCode]): SourceCode = {
SourceCode(s"""
package $pkg

// AUTO-GENERATED FILE, DO NOT MODIFY
object Enums {
${items.mkString("\n")}
}

""".stripMargin)
  }

  def writeToFile(f: Path, str: SourceCode): Unit = {
    Files.createDirectories(f.getParent)
    Files.write(f, str.s.getBytes)
  }
}
