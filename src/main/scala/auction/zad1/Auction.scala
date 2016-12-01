package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._

import auction.zad1.Messages._

object Auction {
    //STATE
    sealed trait AuctionState
    case object Created extends AuctionState
    case object Ignored extends AuctionState
    case object Activated extends AuctionState
    case object Sold extends AuctionState

    //DATA
    sealed trait AuctionData
    // case object Uninitialized extends AuctionData
    final case class StartingPrice(price: Int) extends AuctionData
    final case class Data(winner: ActorRef, highestBid: Int) extends AuctionData

    //INTERNAL MESSAGES
    final val BID_TIMER: String = "BIDT"
    case object BidTimerExpired
    final val DELETE_TIMER: String = "DELT"
    case object DeleteTimerExpired
    final val AUCTION_EXPIRED: String = "EXP"

    //OTHER
    def apply(startingPrice: Int, bidTime: FiniteDuration, deleteTime: FiniteDuration, title: String, seller: ActorRef) = Props(new Auction(startingPrice, bidTime, deleteTime, title, seller))
}

class Auction(var startingPrice: Int, var bidTime: FiniteDuration, var deleteTime: FiniteDuration,var title: String, var seller: ActorRef) extends LoggingFSM[Auction.AuctionState, Auction.AuctionData] {
    import context._
    import Auction._
    startWith(Created, StartingPrice(startingPrice))

    override def preStart() {
        setTimer(BID_TIMER, BidTimerExpired, bidTime, false)
        AuctionSearch.getAuctionSearch(context) ! AuctionCreated(title)
    }

    when(Created) {
        case Event(Bid(bid), StartingPrice(price)) =>
            if(price < bid) {
                Notifier.getNotifier(context) ! Notify(title, sender.path.toString, bid)
                goto(Activated) using Data(sender, bid)
            } else {
                stay using StartingPrice(price) replying BidTooLow(bid)
            }
        case Event(BidTimerExpired, StartingPrice(price)) =>
            goto(Ignored) using StartingPrice(price)
    }

    when(Ignored) {
        case Event(DeleteTimerExpired, _) => stop()
        case Event(Relist, StartingPrice(price)) =>
            println("Auction " + self.path + " expired")
            goto(Created) using StartingPrice(price)
    }

    when(Activated) {
        case Event(Bid(bid), Data(previousWinner, previousBid)) =>
            if(previousBid < bid) {
                previousWinner ! BidTopped(previousBid, bid)
                Notifier.getNotifier(context) ! Notify(title, sender.path.toString, bid)
                stay using Data(sender, bid)
            } else {
                stay using Data(previousWinner, previousBid) replying BidTooLow(bid)
            }
        case Event(BidTimerExpired, Data(winner, winnerBid)) =>
            goto(Sold) using Data(winner, winnerBid)
    }

    when(Sold) {
        case Event(DeleteTimerExpired, _) => stop()
    }

    onTransition {
        case Created -> Ignored =>
            cancelTimer(BID_TIMER)
            setTimer(DELETE_TIMER, DeleteTimerExpired, deleteTime, false)
        case Ignored -> Created =>
            cancelTimer(DELETE_TIMER)
            setTimer(BID_TIMER, BidTimerExpired, bidTime, false)
        case Activated -> Sold =>
            setTimer(DELETE_TIMER, DeleteTimerExpired, deleteTime, false)
            stateData match {
                case Data(winner, winnerBid) =>
                    winner ! WonAuction(winnerBid)
                    seller ! AuctionSold(winner, winnerBid)
                    println("Auction " + self.path + " sold to " + winner.path + " for " + winnerBid)
                case _ =>
                    println("Auction " + self.path + " is in unexpected state")
            }
    }

    onTermination {
        case StopEvent(FSM.Normal, Ignored, _) =>
          AuctionSearch.getAuctionSearch(context) ! AuctionExpired
          seller ! AuctionExpired
        case StopEvent(FSM.Normal, Sold, Data(winner, highestBid)) =>
          AuctionSearch.getAuctionSearch(context) ! AuctionSold(winner, highestBid)
    }

    initialize()

    private def notifyPreviousWinner(previousWinner: ActorRef, previousBid: Int, bid: Int) {
        previousWinner ! BidTopped(previousBid, bid)
    }
}
