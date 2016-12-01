package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._

import auction.zad1.Messages._
import auction.zad1.Util._

object AuctionSearch {
  val AUCTION_SEARCH_PATH = MasterSearch.MASTER_SEARCH_PATH
  def getAuctionSearch(context: ActorContext): ActorSelection = context.actorSelection("/user/" + AuctionSearch.AUCTION_SEARCH_PATH)
  def apply() = Props(new AuctionSearch())
}

class AuctionSearch extends Actor {
  var auctions: Map[String, ActorRef] = Map()

  override def preStart() {
    println("AuctionSearch available at " + self.path)
  }

  override def receive: Receive = LoggingReceive {
    case AuctionCreated(title) =>
      addNewAuction(title, sender)
    case AuctionQuery(query) =>
      queryForAuction(query, sender)
    case _: AuctionEnded =>
      removeAuction(sender)
  }

  private def addNewAuction(title: String, auction: ActorRef) {
    auctions += title -> auction
  }

  private def queryForAuction(query: String, buyer: ActorRef) {
    buyer ! AuctionQueryResult(auctions.filterKeys(_ contains query))
  }

  private def removeAuction(auction: ActorRef) {
    for ((title, a) <- auctions) if (auction == a) auctions -= title
  }
}
