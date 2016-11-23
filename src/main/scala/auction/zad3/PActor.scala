package auction.zad3

import java.time.LocalDateTime

import akka.actor._
import akka.persistence._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._

import auction.zad1.Util._

case object PersistenceFinished
case object Persisted
case object StartActor
case object SendEvents
case class Cmd(data: String)
case class Evt(data: String)

object ExamplePersistentActor {
  def apply() = Props(new ExamplePersistentActor)
}

class ExamplePersistentActor extends PersistentActor {
  override def persistenceId = "sample-id-1"
  var time: Long = 0
  var state: String = ""
  val receiveRecover: Receive = {
    case RecoveryCompleted => context.actorSelection("/user/tester") ! PersistenceFinished
    case evt: Evt =>
  }

  val receiveCommand: Receive = {
    case Cmd(data) =>
      println(s"recv: ${data}")
      val time0 = System.currentTimeMillis
      persist(Evt(s"${data}"))(updateState)
      val time1 = System.currentTimeMillis
      time += (time1 - time0)
    case "print" => println("[TIME] took " + time + " ms")
  }
  def updateState(event: Evt): Unit =
    state = event.data
}

object TestingActor {
  def apply() = Props(new TestingActor())
}

class TestingActor extends Actor {
  var timeStart: Long = 0
  var pactor: ActorRef = null
  override def receive: Receive = LoggingReceive {
    case StartActor => {
      timeStart = System.currentTimeMillis
      pactor = context.actorOf(ExamplePersistentActor(), "pactor")
    }
    case SendEvents => {
      1000 times {
        pactor ! Cmd("xd")
      }
      pactor ! "print"
    }
    case PersistenceFinished => {
      val now = System.currentTimeMillis
      printf("[TIME] Restoring took " + (now - timeStart) + " ns.")
    }
  }
}

object AuctionSystem extends App {
  private val system: ActorSystem = ActorSystem("zad3")
  createSystem(system)
  Await.result(system.whenTerminated, Duration.Inf)
  private def createSystem(system: ActorSystem) {
    import system._
    val tester = system.actorOf(TestingActor(), "tester")
    tester ! StartActor
    tester ! SendEvents
  }
}
