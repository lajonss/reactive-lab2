package auction.zad3

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._

import auction.zad3.Messages._

object Messages {
    final case class Bid(bid: Int)
    final case class BidTooLow(bid: Int)
    final case class BidTopped(prevBid: Int, newBid: Int)

    case object AuctionExpired
    case object Relist
    final case class WonAuction(bid: Int)
}

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
    def apply(startingPrice: Int, bidTime: FiniteDuration, deleteTime: FiniteDuration, manager: ActorRef) = Props(new Auction(startingPrice, bidTime, deleteTime, manager))
}

class Auction(var startingPrice: Int, var bidTime: FiniteDuration, var deleteTime: FiniteDuration, var manager: ActorRef) extends LoggingFSM[Auction.AuctionState, Auction.AuctionData] {
    import context._
    import Auction._
    startWith(Created, StartingPrice(startingPrice))

    override def preStart() {
        setTimer(BID_TIMER, BidTimerExpired, bidTime, false)
    }

    when(Created) {
        case Event(Bid(bid), StartingPrice(price)) =>
            if(price < bid) {
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
                    println("Auction " + self.path + " sold to " + winner.path + " for " + winnerBid)
            }
    }

    onTermination {
        case StopEvent(FSM.Normal, _, _) => manager ! AuctionExpired
    }

    initialize()

    private def notifyPreviousWinner(previousWinner: ActorRef, previousBid: Int, bid: Int) {
        previousWinner ! BidTopped(previousBid, bid)
    }
}

object Buyer {
    def apply(money: Int, auctions: List[ActorRef], manager: ActorRef) = Props(new Buyer(money, auctions, manager))
}

class Buyer(var money: Int, var auctions: List[ActorRef], var manager: ActorRef) extends Actor {
    override def receive: Receive = LoggingReceive {
        case WonAuction(x) => handleWonAuction(sender, x)
        case BidTopped(bidAmount, _) => handleBidTopped(sender, bidAmount)
        case BidTooLow(bidAmount) => handleBidTopped(sender, bidAmount)
    }

    override def preStart() {
        for (auction <- auctions) handleBidTopped(auction, 0)
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

object AuctionManager {
    def apply() = Props(new AuctionManager())
}

class AuctionManager extends Actor {
    private var activeAuctions: Int = 0
    private var auctions: List[ActorRef] = List()

    override def preStart() {
        activeAuctions = 4

        val auction1 = context.actorOf(Auction(0, 15 seconds, 15 seconds, self), "auction1")
        val auction2 = context.actorOf(Auction(0, 15 seconds, 15 seconds, self), "auction2")
        val auction3 = context.actorOf(Auction(10, 15 seconds, 15 seconds, self), "auction3")
        val auction4 = context.actorOf(Auction(50, 15 seconds, 15 seconds, self), "auction4")

        auctions = List(auction1, auction2, auction3, auction4)

        val buyer1 = context.actorOf(Buyer(10, auctions, self), "buyer1")
        val buyer2 = context.actorOf(Buyer(20, auctions, self), "buyer2")
        val buyer3 = context.actorOf(Buyer(10, auctions, self), "buyer3")
        val buyer4 = context.actorOf(Buyer(40, auctions, self), "buyer4")
        val buyer5 = context.actorOf(Buyer(10, auctions, self), "buyer5")
        val buyer6 = context.actorOf(Buyer(60, auctions, self), "buyer6")
        val buyer7 = context.actorOf(Buyer(10, auctions, self), "buyer7")
        val buyer8 = context.actorOf(Buyer(80, auctions, self), "buyer8")
    }

    override def receive: Receive = LoggingReceive {
        case AuctionExpired => checkEnd()
    }

    private def checkEnd() {
        activeAuctions = activeAuctions - 1
        if(activeAuctions == 0) {
            context.system.terminate
        }
    }
}

object AuctionSystem extends App {
    private val system: ActorSystem = ActorSystem("zad3")
    val auctionManager = system.actorOf(AuctionManager(), "auctionManager3")

    Await.result(system.whenTerminated, Duration.Inf)
}
