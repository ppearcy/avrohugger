package avrohugger
package input
package parsers

import reflectivecompilation._
import reflectivecompilation.schemagen._

import org.apache.avro.Protocol
import org.apache.avro.Schema
import org.apache.avro.Schema.Parser
import org.apache.avro.SchemaParseException
import org.apache.avro.compiler.idl.Idl
import org.apache.avro.compiler.idl.ParseException
import scala.collection.JavaConverters._
import java.nio.charset.Charset

import scala.reflect.runtime.universe._
import scala.reflect.runtime.currentMirror
import scala.tools.reflect.ToolBox

// tries schema first, then protocol, then idl, then for case class defs
class StringInputParser {

  lazy val schemaParser = new Parser()

  def getSchemas(inputString: String): List[Schema] = {

    def trySchema(str: String) = {
      try {
        List(schemaParser.parse(inputString))} 
      catch {
        case notSchema: SchemaParseException => tryProtocol(inputString)
        case unknown: Throwable => sys.error("Unexpected exception: " + unknown)
      }
    }

    def tryProtocol(protocolStr: String): List[Schema] = {
      try {
        Protocol.parse(protocolStr).getTypes().asScala.toList}
      catch {
        case notProtocol: SchemaParseException => tryIDL(inputString)
        case unknown: Throwable => sys.error("Unexpected exception: " + unknown)
      }
    }

    def tryIDL(idlString: String): List[Schema] = {
      try {
        val bytes = idlString.getBytes(Charset.forName("UTF-8"))
        val inStream = new java.io.ByteArrayInputStream(bytes)
        val idlParser = new Idl(inStream)
        val protocol = idlParser.CompilationUnit()
        val types = protocol.getTypes
        types.asScala.toList}
      catch {
        case notIDL: ParseException => tryCaseClass(inputString)
        case unknown: Throwable => sys.error("Unexpected exception: " + unknown)
        }
      }

    def tryCaseClass(codeStr: String): List[Schema] = {
      val compilationUnits = PackageSplitter.getCompilationUnits(codeStr)
      val trees = compilationUnits.map(compUnit => Toolbox.toolBox.parse(compUnit))
      val schemas = trees.flatMap(tree => TreeInputParser.parse(tree))
      TypecheckDependencyStore.knownClasses.clear
      schemas
    }

    // tries schema first, then protocol, then idl, then for case class defs
    val schemas: List[Schema] = trySchema(inputString)
    schemas
  }
}

