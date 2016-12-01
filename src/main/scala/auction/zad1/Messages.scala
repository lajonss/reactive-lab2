package auction.zad1

import akka.actor._

object Messages {
    final case class Bid(bid: Int)
    final case class BidTooLow(bid: Int)
    final case class BidTopped(prevBid: Int, newBid: Int)

    sealed trait AuctionEnded
    case object AuctionExpired extends AuctionEnded
    final case class AuctionSold(winner: ActorRef, price: Int) extends AuctionEnded
    case object Relist
    final case class WonAuction(bid: Int)
    final case class AuctionCreated(title: String)
    final case class AuctionQuery(query: String)
    final case class AuctionQueryResult(result: Map[String, ActorRef])

    final case class Notify(title: String, who: String, bid: Int)
    case object NotifySuccess
}
