package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._

import auction.zad1.Messages._
import auction.zad1.Util._

object Seller {
  final val prices = Array(10, 20, 30, 40, 50, 60)
  final val DEFAULT_BID_TIME = 30 seconds
  final val DEFAULT_DEL_TIME = 10 seconds

  def apply(titles: List[String]) = Props(new Seller(titles))
}

class Seller(var titles: List[String]) extends Actor {
  import context._
  import Seller._
  var auctionsQuantity: Int = 0
  var auctions: Set[ActorRef] = Set()
  override def receive: Receive = LoggingReceive {
    case AuctionExpired => handleExpiredAuction(sender)
    case AuctionSold(winner, winningBid) => handleSoldAuction(sender, winner, winningBid)
  }

  override def preStart() {
    for (title <- titles) {
      auctions += context.actorOf(Auction(prices(rand.nextInt(prices.length)), DEFAULT_BID_TIME, DEFAULT_DEL_TIME, title, self), title.replace(" ", "_"))
      auctionsQuantity += 1
    }
    for (auction <- auctions) {
      auction ! StartAuction
    }
  }

  private def handleSoldAuction(auction: ActorRef, winner: ActorRef, winningBid: Int) {
    println("[SELLOG] Seller " + self.path + "\n\tsold " + auction.path + "\n\tto " + winner.path + "\n\tfor " + winningBid)
    auctionEnded(auction)
  }

  private def handleExpiredAuction(auction: ActorRef) {
    println("[SELLOG] Seller " + self.path + " did not sell " + auction.path)
    auctionEnded(auction)
  }

  private def auctionEnded(auction: ActorRef) {
    auctionsQuantity -= 1
    auctions -= auction
    if(auctionsQuantity == 0){
      context.stop(self)
    }
  }
}
