scalaVersion := "2.12.12"

name := "catalyzer"
version := "3.0-SNAPSHOT"
organization := "edu.buffalo.cse.odin"

libraryDependencies ++= Seq(
  "org.scala-lang"               % "scala-reflect"             % "2.12.13",
  "org.scala-lang.modules"       %% "scala-parser-combinators" % "1.1.2",
  "com.fasterxml.jackson.core"   % "jackson-core"              % "2.12.1",
  "org.antlr"                    % "antlr4-runtime"            % "4.8",
  "commons-codec"                % "commons-codec"             % "1.15",
  "org.apache.commons"           % "commons-lang3"             % "3.11",
  "org.apache.commons"           % "commons-text"              % "1.9",
  "org.apache.commons"           % "commons-math3"             % "3.6.1",
  "org.scala-lang.modules"       %% "scala-xml"                % "1.2.0",
  "org.json4s"                   %% "json4s-jackson"           % "3.6.10",
  "org.codehaus.janino"          % "janino"                    % "3.0.16",
  "org.codehaus.janino"          % "commons-compiler"          % "3.0.16",
  "com.google.guava"             % "guava"                     % "30.1-jre",
  "com.fasterxml.jackson.module" %% "jackson-module-scala"     % "2.12.1",
)

antlr4PackageName in Antlr4 := Some("org.apache.spark.sql.catalyst.parser")
antlr4GenVisitor in Antlr4 := true
// antlr4Version in Antlr4 := "4.9.1"
enablePlugins(Antlr4Plugin)

// <- sql/catalyst/core
// <- common/tags
// rm org.apache.spark.sql.catalyst.expressions.csv

test in assembly := {}
assemblyJarName in assembly := "Catalyzer.jar"
assemblyMergeStrategy in assembly := {
  case "module-info.class" => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}