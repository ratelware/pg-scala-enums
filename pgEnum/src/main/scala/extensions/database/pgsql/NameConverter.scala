package extensions.database.pgsql

object NameConverter {
  def removeTypePostfix(s: String): String = {
    s.replaceFirst("_t$", "")
  }

  def enumNameToScalaClassName(s: String): String = {
    s.replaceAllLiterally("$", "")
  }

  def snakeToCamel(s: String): String = {
    val tokens = s.split("_")
    tokens.map(_.capitalize).mkString
  }

  def enumNameToListName(s: String): String = {
    s"_$s"
  }
}
