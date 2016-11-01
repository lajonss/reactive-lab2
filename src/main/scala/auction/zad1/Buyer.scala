package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._

import auction.zad1.Messages._

object Buyer {
    def apply(money: Int, subject: String) = Props(new Buyer(money, subject))
}

class Buyer(var money: Int, var subject: String) extends Actor {
    import context._
    var auctions: Map[String, ActorRef] = Map()
    override def receive: Receive = LoggingReceive {
        case AuctionQueryResult(result) => handleQueryResult(result)
        case WonAuction(x) => handleWonAuction(sender, x)
        case BidTopped(bidAmount, _) => handleBidTopped(sender, bidAmount)
        case BidTooLow(bidAmount) => handleBidTopped(sender, bidAmount)
    }

    override def preStart() {
      AuctionSearch.getAuctionSearch(context) ! AuctionQuery(subject)
    }

    private def handleQueryResult(result: Map[String, ActorRef]) {
      if(result.isEmpty) {
        context.stop(self)
      } else {
        auctions = result
        for ((_, auction) <- auctions) handleBidTopped(auction, 0)
      }
    }

    private def handleWonAuction(auction: ActorRef, x: Int) {
        println("Won auction " + auction.path  + " by " + self.path + " for " + x)
    }

    private def handleBidTopped(auction: ActorRef, bidAmount: Int) {
        money += bidAmount
        val bidAmount1 = bidAmount + 2
        if(bidAmount1 <= money) {
            money = money - bidAmount1
            auction ! Bid(bidAmount1)
        }
    }
}
