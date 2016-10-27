package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent.Await
import scala.concurrent.duration._

import auction.zad1.Messages._

object Messages {
    final case class Bid(bid: Int)
    case object FinishAuction

    final case class SuccessfullBid(amount: Int)
    final case class BidLowerThanStartingPrice(amount: Int)
    final case class BidLowerThanPreviousBid(amount: Int)
    final case class BidTopped(amount: Int)
    final case class WonAuction(amount: Int)
    final case class StartBidding(auction: ActorRef)

    case object StartedBidding
    case object StoppedBidding
    case object Start
}

object Auction {
    def apply(highestBid: Int) = Props(new Auction(highestBid))
}

class Auction(var highestBid: Int) extends Actor {
    private var winner: ActorRef = null
    private var bid: Boolean = false

    override def receive: Receive = LoggingReceive {
        case Bid(newBid) => handleNewBid(newBid, sender)
        case FinishAuction => finishAuction()
    }

    private def handleNewBid(newBid: Int, sender: ActorRef) {
        if(highestBid < newBid) {
            if(winner != null) {
                winner ! BidTopped(highestBid)
            }
            highestBid = newBid
            bid = true
            sender ! SuccessfullBid(newBid)
            winner = sender
        } else {
            if(bid) {
                sender ! BidLowerThanPreviousBid(newBid)
            } else {
                sender ! BidLowerThanStartingPrice(newBid)
            }
        }
    }

    private def finishAuction() {
        if(winner != null) {
            winner ! WonAuction(highestBid)
        }
    }
}

object Buyer {
    def apply(money: Int, manager: ActorRef) = Props(new Buyer(money, manager))
}

class Buyer(var money: Int, var manager: ActorRef) extends Actor {
    private var wonAuctions: Set[ActorRef] = Set()

    override def receive: Receive = LoggingReceive {
        case SuccessfullBid(x) => println("Bid: " + x)
        case WonAuction(x) => handleWonAuction(sender, x)
        case BidTopped(bidAmount) => handleBidTopped(sender, bidAmount)
        case BidLowerThanPreviousBid(bidAmount) => handleBidTopped(sender, bidAmount)
        case BidLowerThanStartingPrice(bidAmount) => handleBidTopped(sender, bidAmount)
        case StartBidding(auction) =>
            manager ! StartedBidding
            handleBidTopped(auction, 0)
    }

    private def handleWonAuction(auction: ActorRef, x: Int) {
        wonAuctions += auction
        println("Won auction " + auction.path  + " by " + self.path + " for " + x)
        manager ! StoppedBidding
    }

    private def handleBidTopped(auction: ActorRef, bidAmount: Int) {
        money += bidAmount
        val bidAmount1 = bidAmount + 2
        if(bidAmount1 <= money) {
            money = money - bidAmount1
            auction ! Bid(bidAmount1)
        } else {
            manager ! StoppedBidding
        }
    }
}

object AuctionManager {
    def apply() = Props(new AuctionManager())
}

class AuctionManager() extends Actor {
    private var bidding: Int = 0
    private var auctions: List[ActorRef] = List()
    private var buyers: List[ActorRef] = List()
    private var ending: Boolean = false

    override def receive: Receive = LoggingReceive {
        case StartedBidding => bidding = bidding + 1
        case StoppedBidding => checkEnd()
        case Start => start(self)
    }

    private def checkEnd() {
        if(ending) {
            bidding = bidding - 1
            if(bidding == 0) {
                context.system.terminate
            }
        } else {
            bidding = bidding - 1
            println("Left: " + bidding)
            if(bidding == auctions.size) {
                for (a <- auctions) a ! FinishAuction
                ending = true
                // auctions foreach x: ActorRef => x ! FinishAuction
            }
        }
    }

    private def start(manager: ActorRef) {
        val auction1 = context.actorOf(Auction(0), "auction1")
        val auction2 = context.actorOf(Auction(0), "auction2")
        val auction3 = context.actorOf(Auction(10), "auction3")
        val auction4 = context.actorOf(Auction(50), "auction4")

        auctions = List(auction1, auction2, auction3, auction4)

        val buyer1 = context.actorOf(Buyer(10, manager), "buyer1")
        val buyer2 = context.actorOf(Buyer(20, manager), "buyer2")
        val buyer3 = context.actorOf(Buyer(10, manager), "buyer3")
        val buyer4 = context.actorOf(Buyer(40, manager), "buyer4")
        val buyer5 = context.actorOf(Buyer(10, manager), "buyer5")
        val buyer6 = context.actorOf(Buyer(60, manager), "buyer6")
        val buyer7 = context.actorOf(Buyer(10, manager), "buyer7")
        val buyer8 = context.actorOf(Buyer(80, manager), "buyer8")

        buyers = List(buyer1, buyer2, buyer3, buyer4, buyer5, buyer6, buyer7, buyer8)

        buyer1 ! StartBidding(auction=auction1)
        buyer2 ! StartBidding(auction=auction1)
        buyer3 ! StartBidding(auction=auction1)

        buyer4 ! StartBidding(auction=auction2)
        buyer5 ! StartBidding(auction=auction2)
        buyer6 ! StartBidding(auction=auction2)

        buyer7 ! StartBidding(auction=auction3)
        buyer8 ! StartBidding(auction=auction3)
        buyer1 ! StartBidding(auction=auction3)

        buyer2 ! StartBidding(auction=auction4)
        buyer3 ! StartBidding(auction=auction4)
        buyer4 ! StartBidding(auction=auction4)
        buyer5 ! StartBidding(auction=auction4)
        buyer6 ! StartBidding(auction=auction4)
    }
}

object AuctionSystem extends App {
    private val system: ActorSystem = ActorSystem("zad1")
    val auctionManager = system.actorOf(AuctionManager(), "auctionManager1")
    auctionManager ! Start

    Await.result(system.whenTerminated, Duration.Inf)
}
