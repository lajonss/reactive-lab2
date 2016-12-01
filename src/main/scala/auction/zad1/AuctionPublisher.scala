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
    import AuctionPublisher._
    private var message_id = 1
    override def receive: Receive = LoggingReceive {
        case Notify(title, who, bid) =>
            println("[AUCTIONPUBLISHER] #" + message_id)
            println("\ttitle: " + title)
            println("\twinner: " + who)
            println("\tprice: " + bid)
            message_id += 1
            sender ! NotifySuccess
    }
    override def preStart() = {
        println("[AUCTIONPUBLISHER] Listening at " + self.path)
        println("\t\t" + PATH)
    }
}

object AuctionPublisherApp extends App {
    import AuctionPublisher._
    val config = ConfigFactory.load()
    val system = ActorSystem(SYSTEM, config.getConfig("auctionpublisher").withFallback(config))
    val publisher = system.actorOf(AuctionPublisher(), ACTOR)
    Await.result(system.whenTerminated, Duration.Inf)
}
