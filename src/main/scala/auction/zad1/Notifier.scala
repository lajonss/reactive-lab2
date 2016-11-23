package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
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

class Notifier(var publisher: ActorSelection) extends Actor {
  import context._
  import Notifier._
  override def receive: Receive = LoggingReceive {
    case a: Notify => publisher ! a
  }
  override def preStart() {
    println("Notifier available at " + self.path)
  }
}
