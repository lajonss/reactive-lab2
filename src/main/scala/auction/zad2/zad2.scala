package auction.zad2

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._

import auction.zad2.Messages._

object Messages {
    case object BidTimerExpired
    case object DeleteTimerExpired
    case object Relist
    case class AuctionFinished(auction: ActorRef)
    case object AuctionNotActive
    case object AuctionAlreadySold

    sealed trait AuctionState
    case object Created extends AuctionState
    case object Ignored extends AuctionState
    case object Activated extends AuctionState
    case object Sold extends AuctionState

    //zad1 messages
    final case class Bid(bid: Int)

    final case class SuccessfullBid(amount: Int)
    final case class BidLowerThanStartingPrice(amount: Int)
    final case class BidLowerThanPreviousBid(amount: Int)
    final case class BidTopped(amount: Int)
    final case class WonAuction(amount: Int)
    final case class StartBidding(auction: ActorRef)

    case object Start
}

object Auction {
    def apply(highestBid: Int, bidTime: FiniteDuration, deleteTime: FiniteDuration, manager: ActorRef) = Props(new Auction(highestBid, bidTime, deleteTime, manager))
}

class Auction(var highestBid: Int, var bidTime: FiniteDuration, var deleteTime: FiniteDuration, var manager: ActorRef) extends Actor {
    import context._
    private var winner: ActorRef = null
    private var state: AuctionState = Created

    override def preStart() {
        context.system.scheduler.scheduleOnce(bidTime, self, BidTimerExpired)
    }

    override def receive: Receive = LoggingReceive {
        case BidTimerExpired => bidTimerExpired()
        case DeleteTimerExpired => deleteTimerExpired()
        case Relist => relist()
        case Bid(newBid) => handleNewBid(newBid, sender)
    }

    private def bidTimerExpired() {
        if(state == Created) {
            state = Ignored
            context.system.scheduler.scheduleOnce(deleteTime, self, DeleteTimerExpired)
        } else if(state == Activated) {
            state = Sold
            context.system.scheduler.scheduleOnce(deleteTime, self, DeleteTimerExpired)
            winner ! WonAuction(highestBid)
            println("Sold " + self.path + " for " + highestBid)
        } else {
            println("[ERROR] Unexpected BidTimerExpired at state " + state)
        }
    }

    private def deleteTimerExpired() {
        if(state == Ignored) {
            println("Deleted ignored item " + self.path)
            manager ! AuctionFinished(self)
            context.stop(self)
        } else if(state == Sold) {
            println("Deleted sold item " + self.path)
            manager ! AuctionFinished(self)
            context.stop(self)
        } else {
            println("[ERROR] Unexpected DeleteTimerExpired at state " + state)
        }
    }

    private def relist() {
        if(state == Ignored) {
            state = Created
            preStart()
        } else {
            println("[ERROR] Unexpected Relist at state " + state)
        }
    }

    private def handleNewBid(newBid: Int, sender: ActorRef) {
        if(state == Created) {
            if(newBid > highestBid) {
                state = Activated
                winner = sender
                sender ! SuccessfullBid(newBid)
            } else {
                sender ! BidLowerThanStartingPrice(newBid)
            }
        } else if(state == Ignored) {
            sender ! AuctionNotActive
        } else if(state == Sold) {
            sender ! AuctionAlreadySold
        } else if(state == Activated) {
            if(highestBid < newBid) {
                if(winner != null) {
                    winner ! BidTopped(highestBid)
                }
                highestBid = newBid
                sender ! SuccessfullBid(newBid)
                winner = sender
            } else {
                sender ! BidLowerThanPreviousBid(newBid)
            }
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
        case StartBidding(auction) => handleBidTopped(auction, 0)
    }

    private def handleWonAuction(auction: ActorRef, x: Int) {
        wonAuctions += auction
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

class AuctionManager() extends Actor {
    private var activeAuctions: Int = 4
    private var auctions: List[ActorRef] = List()
    private var buyers: List[ActorRef] = List()
    private var ending: Boolean = false

    override def receive: Receive = LoggingReceive {
        case Start => start(self)
        case AuctionFinished(_) =>  checkEnd()
    }

    private def checkEnd() {
        activeAuctions = activeAuctions - 1
        if(activeAuctions == 0) {
            context.system.terminate
        }
    }

    private def start(manager: ActorRef) {
        val auction1 = context.actorOf(Auction(0, 15 seconds, 15 seconds, self), "auction1")
        val auction2 = context.actorOf(Auction(0, 15 seconds, 15 seconds, self), "auction2")
        val auction3 = context.actorOf(Auction(10, 15 seconds, 15 seconds, self), "auction3")
        val auction4 = context.actorOf(Auction(50, 15 seconds, 15 seconds, self), "auction4")

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
    private val system: ActorSystem = ActorSystem("zad2")
    val auctionManager = system.actorOf(AuctionManager(), "auctionManager2")
    auctionManager ! Start

    Await.result(system.whenTerminated, Duration.Inf)
}
