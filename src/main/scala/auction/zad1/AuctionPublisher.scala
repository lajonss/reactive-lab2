package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._
import com.typesafe.config._

import auction.zad1.Messages._
import auction.zad1.Util._

object AuctionPublisher {
  final val SYSTEM: String = "publisher"
  final val ACTOR: String = "AuctionPublisher"
  final val PATH: String = "akka.tcp://" + SYSTEM + "@127.0.0.1:2552/user/" + ACTOR
  def apply() = Props(new AuctionPublisher())
}

class AuctionPublisher extends Actor {
  override def receive: Receive = LoggingReceive {
    case Notify(title, who, bid) => println("[" + title + "] " + who + " bid " + bid)
  }
  override def preStart() = {
    println("AuctionPublisher listening at " + self.path)
  }
}

object AuctionPublisherApp extends App {
  import AuctionPublisher._
  val config = ConfigFactory.load()
  private val system: ActorSystem = ActorSystem(SYSTEM, config.getConfig("publisher").withFallback(config))
  private val publisher = system.actorOf(AuctionPublisher(), ACTOR)
  Await.result(system.whenTerminated, Duration.Inf)
}
