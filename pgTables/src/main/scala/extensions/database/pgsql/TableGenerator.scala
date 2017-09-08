package extensions.database.pgsql

import scala.concurrent.duration._
import slick.sql.SqlProfile.ColumnOption

import scala.concurrent.Await

// stolen from https://github.com/tminglei/slick-pg/blob/master/examples/codegen-customization/codegen/src/main/scala/demo/CustomizedCodeGenerator.scala
/**
  *  This customizes the Slick code generator. We only do simple name mappings.
  *  For a more advanced example see https://github.com/cvogt/slick-presentation/tree/scala-exchange-2013
  */
object TableGenerator {
  import scala.concurrent.ExecutionContext.Implicits.global

  val slickProfile = database.model.profile.ExtendedPostgresProfile
  val jdbcDriver = "org.postgresql.Driver"

  def main(args: Array[String]): Unit = {
    val dbPath = args(0)
    val targetDirectory = args(1)
    val packageName = args(2) +".tables"

    println("Generating code for SQL table mapping.")
    println(s"Fetching data from DB at ${dbPath} to ${targetDirectory}(package: ${packageName})")

    val db = slickProfile.api.Database.forURL(dbPath,driver=jdbcDriver)
    // filter out desired tables
    lazy val codegen = db.run {
      slickProfile.defaultTables
        .flatMap( slickProfile.createModelBuilder(_, true).buildModel )
    }.map { model =>
      new slick.codegen.SourceCodeGenerator(model) {
        println("Successfully fetched model from DB, starting code generation")

        override def Table = new Table(_) { table =>
          override def Column = new Column(_) { column =>
            // customize db type -> scala type mapping, pls adjust it according to your environment
            override def rawType: String = model.tpe match {
              case "java.sql.Date" => "java.time.LocalDate"
              case "java.sql.Time" => "java.time.LocalTime"
              case "java.sql.Timestamp" => "java.time.LocalDateTime"
              // currently, all types that's not built-in support were mapped to `String`
              case "String" => model.options.find(_.isInstanceOf[ColumnOption.SqlType]).map(_.asInstanceOf[ColumnOption.SqlType].typeName).map({
                case "hstore" => "Map[String, String]"
                case "geometry" => "com.vividsolutions.jts.geom.Geometry"
                case "int8[]" => "List[Long]"
                case "json" => "argonaut.Json"
                case "jsonb" => "argonaut.Json"
                case "text" => "String"
                case s => s"${NameConverter.snakeToCamel(NameConverter.removeTypePostfix(s))}.Value"
              }).getOrElse("String")
              case rt => super.rawType
            }
          }
        }

        // ensure to use our customized postgres driver at `import profile.simple._`
        override def packageCode(profile: String, pkg: String, container: String, parentType: Option[String]) : String = {
          s"""
package ${pkg}
// AUTO-GENERATED Slick data model
// DO NOT MODIFY! IT WILL BE GENERATED ON EACH COMPILATION
/** Stand-alone Slick data model for immediate use */
object ${container} extends {
  val profile = ${profile}
} with ${container}
/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait ${container}${parentType.map(t => s" extends $t").getOrElse("")} {
  val profile: $profile
  import profile.api._
  import database.model.enums.Enums._
  ${indent(code)}
}
      """.trim()
        }
      }
    }.recoverWith({
      case e =>
        println(" ============ Code generation failed! ============")
        println(s"Error: $e")
        e.printStackTrace()
        throw e
    })

    // write the generated results to file
    Await.ready(
      codegen.map(
        _.writeToFile(
          "database.model.profile.ExtendedPostgresProfile", // use our customized postgres driver
          targetDirectory,
          packageName
        )
      ),
      20.seconds
    )

    println("SQL access code generation completed")
  }
}
