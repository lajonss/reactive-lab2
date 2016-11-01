package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._

import auction.zad1.Messages._
import auction.zad1.Util._

object AuctionSystem extends App {
    private val system: ActorSystem = ActorSystem("zad1")
    val auctionSearch = system.actorOf(AuctionSearch(), AuctionSearch.AUCTION_SEARCH_PATH)
    createSystem(system, 20, 5, 10)

    Await.result(system.whenTerminated, Duration.Inf)

    private def createSystem(system: ActorSystem, nBuyers: Int, nSellers: Int, nAuctions: Int) {
      for (i <- 1 to nSellers)
        system.actorOf(Seller(nAuctions), "seller" + i)
      for (i <- 1 to nBuyers)
        system.actorOf(Buyer(getRandomAmountOfMoney(), getRandomKeyword()), "buyer" + i)
    }
}
