package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._
import akka.persistence._
import akka.persistence.fsm._
import akka.persistence.fsm.PersistentFSM.FSMState
import scala.reflect._

import java.time.LocalDateTime

import auction.zad1.Messages._
import auction.zad1.Util._

object Auction {
    //STATE
    sealed trait AuctionState extends FSMState
    case object Created extends AuctionState {
      override def identifier: String = "CR"
    }
    case object Ignored extends AuctionState {
      override def identifier: String = "IG"
    }
    case object Activated extends AuctionState {
      override def identifier: String = "AC"
    }
    case object Sold extends AuctionState {
      override def identifier: String = "SO"
    }
    case object Ended extends AuctionState {
      override def identifier: String = "EN"
    }

    //DATA
    sealed trait AuctionData
    // case object Uninitialized extends AuctionData
    final case class StartingPrice(price: Int) extends AuctionData
    final case class StartedPrice(at: LocalDateTime, price: Int) extends AuctionData
    final case class Data(at: LocalDateTime, winner: ActorRef, highestBid: Int) extends AuctionData

    //INTERNAL MESSAGES
    final val BID_TIMER: String = "BIDT"
    case object BidTimerExpired
    final val DELETE_TIMER: String = "DELT"
    case object DeleteTimerExpired
    final val AUCTION_EXPIRED: String = "EXP"

    //EVENTS
    sealed trait AuctionEvent
    case class BidTimerStarted(at: LocalDateTime) extends AuctionEvent
    case class DeleteTimerStarted(at: LocalDateTime) extends AuctionEvent
    case class Relisted(at: LocalDateTime) extends AuctionEvent
    case class Bidded(at:LocalDateTime, bid: Int, bidder: ActorRef) extends AuctionEvent
    case object AuctionEnded extends AuctionEvent

    //OTHER
    def apply(startingPrice: Int, bidTime: FiniteDuration, deleteTime: FiniteDuration, title: String, seller: ActorRef) = Props(new Auction(startingPrice, bidTime, deleteTime, title, seller))
}

class Auction(var startingPrice: Int, var bidTime: FiniteDuration, var deleteTime: FiniteDuration,var title: String, var seller: ActorRef) extends LoggingPersistentFSM[Auction.AuctionState, Auction.AuctionData, Auction.AuctionEvent] with PersistentFSM[Auction.AuctionState, Auction.AuctionData, Auction.AuctionEvent]{
    import context._
    import Auction._

    override def persistenceId = "persistent-auction-" + title.replace(" ", "_")
    override def domainEventClassTag: ClassTag[AuctionEvent] = classTag[AuctionEvent]

    startWith(Created, StartingPrice(startingPrice))

    when(Created) {
        case Event(StartAuction, _) =>
          AuctionSearch.getAuctionSearch(context) ! AuctionCreated(title)
          setTimer(BID_TIMER, BidTimerExpired, bidTime, false)
          stay applying BidTimerStarted(LocalDateTime.now)
        case Event(Bid(bid), StartedPrice(_, price)) =>
            if(price < bid) {
                goto(Activated) applying Bidded(LocalDateTime.now, bid, sender)
            } else {
                stay replying BidTooLow(bid)
            }
        case Event(BidTimerExpired, _) =>
            cancelTimer(BID_TIMER)
            setTimer(DELETE_TIMER, DeleteTimerExpired, deleteTime, false)
            goto(Ignored) applying DeleteTimerStarted(LocalDateTime.now)
    }

    when(Ignored) {
        case Event(DeleteTimerExpired, _) => {
          AuctionSearch.getAuctionSearch(context) ! AuctionExpired
          seller ! AuctionExpired
          goto(Ended) applying AuctionEnded
        }
        case Event(Relist, _) =>
            println("Auction " + self.path + " expired")
            cancelTimer(DELETE_TIMER)
            setTimer(BID_TIMER, BidTimerExpired, bidTime, false)
            goto(Created) applying Relisted(LocalDateTime.now)
    }

    when(Activated) {
        case Event(Bid(bid), Data(_, previousWinner, previousBid)) =>
            if(previousBid < bid) {
                previousWinner ! BidTopped(previousBid, bid)
                stay applying Bidded(LocalDateTime.now, bid, sender)
            } else {
                stay replying BidTooLow(bid)
            }
        case Event(BidTimerExpired, Data(_, winner, winnerBid)) =>
            cancelTimer(BID_TIMER)
            setTimer(DELETE_TIMER, DeleteTimerExpired, deleteTime, false)
            winner ! WonAuction(winnerBid)
            seller ! AuctionSold(winner, winnerBid)
            println("Auction " + self.path + " sold to " + winner.path + " for " + winnerBid)
            goto(Sold) applying DeleteTimerStarted(LocalDateTime.now)
    }

    when(Sold) {
        case Event(DeleteTimerExpired, Data(_, winner, highestBid)) => {
          AuctionSearch.getAuctionSearch(context) ! AuctionSold(winner, highestBid)
          goto(Ended) applying AuctionEnded
        }
    }

    when(Ended) {
      case _ => stay
    }

    onTransition {
      case _ -> Ended => stop()
    }

    //initialize()

    private def notifyPreviousWinner(previousWinner: ActorRef, previousBid: Int, bid: Int) {
        previousWinner ! BidTopped(previousBid, bid)
    }

    override def applyEvent(event: AuctionEvent, dataBeforeEvent: AuctionData): AuctionData = {
      event match {
        case Bidded(_, bid, bidder) =>  {
          dataBeforeEvent match {
            case StartedPrice(at, _) => Data(at, bidder, bid)
            case Data(at, _, _) => Data(at, bidder, bid)
          }
        }
        case Relisted(time) => {
          dataBeforeEvent match {
            case StartedPrice(_, price) => StartedPrice(time, price)
          }
        }
        case BidTimerStarted(time) => {
          dataBeforeEvent match {
            case StartingPrice(price) => StartedPrice(time, price)
            case StartedPrice(_, price) => StartedPrice(time, price)
          }
        }
        case DeleteTimerStarted(time) => {
          dataBeforeEvent match {
            case StartedPrice(_, price) => StartedPrice(time, price)
            case Data(_, bidder, bid) => Data(time, bidder, bid)
          }
        }
        case AuctionEnded => dataBeforeEvent
      }
    }

    override def onRecoveryCompleted(): Unit = {
      super.onRecoveryCompleted()
      stateName match {
        case Ignored => restoreDeleteTimer()
        case Sold => restoreDeleteTimer()
        case Created => restoreBidTimer()
        case Activated => restoreBidTimer()
        case Ended =>
      }
    }

    private def restoreDeleteTimer() {
      AuctionSearch.getAuctionSearch(context) ! AuctionCreated(title)
      val now = LocalDateTime.now
      stateData match {
        case StartedPrice(at, _) => {
          if(at + deleteTime < now) {
            setTimer(DELETE_TIMER, DeleteTimerExpired, deleteTime - (now - at), false)
          } else {
            self ! DeleteTimerExpired
          }
        }
        case Data(at, _, _) => {
          if(at + deleteTime < now) {
            setTimer(DELETE_TIMER, DeleteTimerExpired, deleteTime - (now - at), false)
          } else {
            self ! DeleteTimerExpired
          }
        }
      }
    }

    private def restoreBidTimer() {
      AuctionSearch.getAuctionSearch(context) ! AuctionCreated(title)
      val now = LocalDateTime.now
      stateData match {
        case StartedPrice(at, _) => {
          if(at + bidTime < now) {
            setTimer(BID_TIMER, BidTimerExpired, bidTime - (now - at), false)
          } else {
            self ! BidTimerExpired
          }
        }
        case Data(at, _, _) => {
          if(at + bidTime < now) {
            setTimer(BID_TIMER, BidTimerExpired, bidTime - (now - at), false)
          } else {
            self ! BidTimerExpired
          }
        }
        case StartingPrice(_) =>
      }
    }
}
