package org.apache.spark.sql.connector.catalog

import org.apache.spark.sql.catalyst.{FunctionIdentifier, TableIdentifier}
import org.apache.spark.sql.catalyst.parser.CatalystSqlParser
import org.apache.spark.sql.AnalysisException

object CatalogV2Implicits {


  implicit class NamespaceHelper(namespace: Array[String]) {
    def quoted: String = namespace.map(quoteIfNeeded).mkString(".")
  }

  implicit class IdentifierHelper(ident: Identifier) {
    def quoted: String = {
      if (ident.namespace.nonEmpty) {
        ident.namespace.map(quoteIfNeeded).mkString(".") + "." + quoteIfNeeded(ident.name)
      } else {
        quoteIfNeeded(ident.name)
      }
    }

    def asMultipartIdentifier: Seq[String] = ident.namespace :+ ident.name

    def asTableIdentifier: TableIdentifier = ident.namespace match {
      case ns if ns.isEmpty => TableIdentifier(ident.name)
      case Array(dbName) => TableIdentifier(ident.name, Some(dbName))
      case _ =>
        throw new AnalysisException(
          s"$quoted is not a valid TableIdentifier as it has more than 2 name parts.")
    }

    def asFunctionIdentifier: FunctionIdentifier = ident.namespace() match {
      case ns if ns.isEmpty => FunctionIdentifier(ident.name())
      case Array(dbName) => FunctionIdentifier(ident.name(), Some(dbName))
      case _ =>
        throw new AnalysisException(
          s"$quoted is not a valid FunctionIdentifier as it has more than 2 name parts.")
    }
  }

  implicit class MultipartIdentifierHelper(parts: Seq[String]) {
    if (parts.isEmpty) {
      throw new AnalysisException("multi-part identifier cannot be empty.")
    }

    def asIdentifier: Identifier = Identifier.of(parts.init.toArray, parts.last)

    def asTableIdentifier: TableIdentifier = parts match {
      case Seq(tblName) => TableIdentifier(tblName)
      case Seq(dbName, tblName) => TableIdentifier(tblName, Some(dbName))
      case _ =>
        throw new AnalysisException(
          s"$quoted is not a valid TableIdentifier as it has more than 2 name parts.")
    }

    def quoted: String = parts.map(quoteIfNeeded).mkString(".")
  }

  def quoteIfNeeded(part: String): String = {
    if (part.contains(".") || part.contains("`")) {
      s"`${part.replace("`", "``")}`"
    } else {
      part
    }
  }

  def parseColumnPath(name: String): Seq[String] = {
    CatalystSqlParser.parseMultipartIdentifier(name)
  }
}