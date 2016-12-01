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

import java.util.concurrent.TimeoutException

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
    private var message_id = 1
    override def preStart() {
        println("[NOTIFIER] Started at " + self.path)
    }
    override def receive: Receive = LoggingReceive {
        case notify: Notify =>
            println("[NOTIFIER] NOTIFY #" + message_id + " : " + notify)
            message_id += 1
            val worker = context.actorOf(NotifierRequest(publisher, notify))
        case NotifySuccess => println("[NOTIFIER] Success")
    }
    override val supervisorStrategy = OneForOneStrategy(loggingEnabled=true) {
        case _:TimeoutException =>
            log.info("[NOTIFIER] Failed to connect, retrying, #" + message_id)
            Restart
        case e =>
            log.error("[NOTIFIER] Unexpected failure: {}", e.getMessage)
            log.error("[NOTIFIER] Shutting down...")
            Stop
    }
}

object NotifierRequest {
    def apply(publisher: ActorSelection, notify: Notify) = Props(new NotifierRequest(publisher, notify))
}

class NotifierRequest(publisher: ActorSelection, notify: Notify) extends Actor {
    override def preStart() {
        self ! notify
    }
    override def receive = LoggingReceive {
        case notify: Notify =>
            println("[NOTIFIER_REQUEST] sending " + notify)
            implicit val timeout = Timeout(5 seconds)
            val future = publisher ? notify
            val result = Await.result(future, timeout.duration)
            context.parent ! result
            println("[NOTIFIER_REQUEST] sent " + notify)
            context.stop(self)
    }
}
