import distributed._
import project._
import model._
import dependencies._
import graph._
import build._
import files._
import distributed.project.resolve.ProjectResolver
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorMain {
  
  // Pluggable components.
  val resolver = new resolve.AggregateProjectResolver(
      Seq(new support.git.GitProjectResolver))
  val depExtractor = new MultiBuildDependencyExtractor(
      Seq(new support.sbt.SbtDependencyExtractor(),
          support.scala.ScalaDependencyExtractor))
  val extractor = new Extractor(resolver, depExtractor)
  val buildRunner = new AggregateBuildRunner(Seq(
      support.scala.ScalaBuildRunner,
      new support.sbt.SbtBuildRunner()))
  
  // Actor systems!
  lazy val actorSystem = ActorSystem()  
  lazy val logDirManager = actorSystem.actorOf(Props(new logging.LogDirManagerActor(new java.io.File(".dlogs"))))
  lazy val logger = new logging.ActorLogger(logDirManager)
  //val logger = logging.ConsoleLogger()
  lazy val extractorActor = actorSystem.actorOf(Props(new ExtractorActor(extractor)), "Project-Dependency-Extractor")
  lazy val baseBuildActor = actorSystem.actorOf(Props(new BuildRunnerActor(buildRunner, resolver)), "Project-Builder")
  lazy val fullBuilderActor = actorSystem.actorOf(Props(new SimpleBuildActor(extractorActor, baseBuildActor)), "simple-distributed-builder")
  def scalaArm =
    BuildConfig("Scala-arm", "sbt", "git://github.com/jsuereth/scala-arm.git#86d3477a7ce91b9046197f9f6f49bf9ff8a137f6", "")
  
  def scalaConfig =
    BuildConfig("scala", "scala", "git://github.com/scala/scala.git#ff016002738580a7de11977c91e0061dbcf270ad", "")
    
  def dBuildConfig =
    DistributedBuildConfig(Seq(scalaConfig, scalaArm))
  
  def parsedDbuildConfig =
    DistributedBuildParser.parseBuildString(repeatableConfig)
    
  def runBuild = {
    import akka.pattern.ask
    import akka.util.duration._
    import akka.util.Timeout
    implicit val timeout: Timeout = (4).hours
    fullBuilderActor ? RunDistributedBuild(parsedDbuildConfig, logger)
  }
    
  def shutdown() = actorSystem.shutdown()
  
  
  def repeatableConfig = """{ "projects":[
     {"name":"scala","system":"scala","uri":"git://github.com/scala/scala.git#4c6522bab70ce8588f5688c9b4c01fe3ff8d24fc","directory":""},
     {"name":"scala-arm","system":"sbt","uri":"git://github.com/jsuereth/scala-arm.git#86d3477a7ce91b9046197f9f6f49bf9ff8a137f6","directory":""}
    ]}"""
}