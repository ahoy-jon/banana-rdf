import sbt._
import Keys._

object BuildSettings {

  val logger = ConsoleLogger()

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization := "org.w3",
    version      := "0.1",
    scalaVersion := "2.9.1",
    parallelExecution in Test := false,
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-optimize", "-Ydependent-method-types")
  )

}

object YourProjectBuild extends Build {

  import BuildSettings._
  
  import com.typesafe.sbteclipse.plugin.EclipsePlugin._
  
  val junitInterface = "com.novocode" % "junit-interface" % "0.8"
  val scalacheck = "org.scala-tools.testing" % "scalacheck_2.9.1" % "1.9"
  val scalatest = "org.scalatest" %% "scalatest" % "1.7.1"
  
  val testsuiteDeps =
    Seq(libraryDependencies += junitInterface,
        libraryDependencies += scalatest,
        libraryDependencies += scalacheck)
  
  val testDeps =
    Seq(libraryDependencies += junitInterface % "test",
        libraryDependencies += scalatest % "test")
  
  val jenaDeps =
    Seq(
      resolvers += "apache-repo-releases" at "http://repository.apache.org/content/repositories/releases/",
      libraryDependencies += "org.apache.jena" % "jena-arq" % "2.9.0-incubating")
  
  lazy val pimpMyRdf = Project(
    id = "pimp-my-rdf",
    base = file("."),
    settings = buildSettings ++ Seq(EclipseKeys.skipParents in ThisBuild := false),
    aggregate = Seq(
      rdf,
      rdfTestSuite,
      simpleRdf,
      n3,
      n3TestSuite,
      jena))
  
  lazy val rdf = Project(
    id = "rdf",
    base = file("rdf"),
    settings = buildSettings ++ testDeps
  )

  lazy val rdfTestSuite = Project(
    id = "rdf-test-suite",
    base = file("rdf-test-suite"),
    settings = buildSettings ++ testsuiteDeps
  ) dependsOn (rdf)

  val simpleRdf = Project(
    id = "simple-rdf",
    base = file("simple-rdf"),
    settings = buildSettings ++ testDeps
  ) dependsOn (rdf, n3, rdfTestSuite % "test", n3TestSuite % "test")
  
  lazy val jena = Project(
    id = "jena",
    base = file("jena"),
    settings = buildSettings ++ jenaDeps ++ testDeps
  ) dependsOn (rdf, n3, simpleRdf % "test", rdfTestSuite % "test", n3TestSuite % "test")

  lazy val n3 = Project(
    id = "n3",
    base = file("n3"),
    settings = buildSettings ++ jenaDeps
  ) dependsOn (rdf)
  
  lazy val n3TestSuite = Project(
    id = "n3-test-suite",
    base = file("n3-test-suite"),
    settings = buildSettings ++ testsuiteDeps ++ jenaDeps
  ) dependsOn (n3)

}

