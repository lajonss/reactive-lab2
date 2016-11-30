package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._

import auction.zad1.Messages._
import auction.zad1.Util._

object Notifier {
  final val LOCAL_NOTIFIER = "Notifier"
  def getNotifier(context: ActorContext): ActorSelection = context.actorSelection("/user/" + LOCAL_NOTIFIER)

  def apply(publisher: ActorSelection) = Props(new Notifier(publisher))
}

class Notifier(var publisher: ActorSelection) extends Actor with ActorLogging {
  import context._
  import Notifier._
  override def receive: Receive = LoggingReceive {
    case a: Notify => val worker = context.actorOf(NotifierRequest(publisher, a))
  }
  override def preStart() {
    println("[NOTIFIER] available at " + self.path)
  }
  override val supervisorStrategy = OneForOneStrategy(loggingEnabled = true) {
    case e =>
      log.error("[NOTIFIER] Unexpected failure: {}", e.getMessage)
      Stop
  }
}

object NotifierRequest {
  def apply(publisher: ActorSelection, notify: Notify) = Props(new NotifierRequest(publisher, notify))
}

class NotifierRequest(publisher: ActorSelection, notify: Notify) extends Actor {
  override def preStart() {
    implicit val timeout = Timeout(5 seconds)
    val future = publisher ? notify
    val result = Await.result(future, timeout.duration)
    context.stop(self)
  }
  override def receive = LoggingReceive {
    case _ =>
  }
}
