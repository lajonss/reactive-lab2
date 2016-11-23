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

object AuctionSystem extends App {
  val config = ConfigFactory.load()
  private val system: ActorSystem = ActorSystem("zad1", config.getConfig("auctionsystem").withFallback(config))
  val auctionSearch = system.actorOf(AuctionSearch(), AuctionSearch.AUCTION_SEARCH_PATH)
  val notifier = system.actorOf(Notifier(system.actorSelection(AuctionPublisher.PATH)), Notifier.LOCAL_NOTIFIER)
  createSystem(system)
  Await.result(system.whenTerminated, Duration.Inf)
  private def createSystem(system: ActorSystem) {
    import system._
    val buyer1 = system.actorOf(Buyer(1000), "buyer1")
    val buyer2 = system.actorOf(Buyer(1000), "buyer2")
    system.scheduler.scheduleOnce(1 second, new Runnable {
      def run() {
        val seller = system.actorOf(Seller(List("warszawa V8 1974")), "seller")
        system.scheduler.scheduleOnce(1 second, new Runnable {
          def run() {
            buyer1 ! GainInterest("warszawa")
            buyer2 ! GainInterest("warszawa")
          }
        })
      }
    })
  }
}
