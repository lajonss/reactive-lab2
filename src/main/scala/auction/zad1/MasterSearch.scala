package auction.zad1

import akka.routing._
import akka.actor._
import akka.event._
import scala.concurrent._

import akka.util.Timeout
import scala.concurrent.duration._

import akka.event.LoggingReceive

import Messages._

object MasterSearch {
    val MASTER_SEARCH_PATH = "MasterSearch"
    def getMasterSearch(context: ActorContext): ActorSelection = context.actorSelection("/user/" + MasterSearch.MASTER_SEARCH_PATH)
    def apply() = Props(new MasterSearch())
}

class MasterSearch extends Actor with ActorLogging {
    import MasterSearch._
    val nRoutees = 16
    val routees = Vector.fill(nRoutees) {
        val r = context.actorOf(AuctionSearch())
        context watch r
        ActorRefRoutee(r)
    }

    var registeringRouter = Router(BroadcastRoutingLogic(), routees)
    var queryRouter = Router(RoundRobinRoutingLogic(), routees)

    override def receive = LoggingReceive {
        case ac: AuctionCreated => registeringRouter.route(ac, sender())
        case aq: AuctionQuery => queryRouter.route(aq, sender())
    }
}
