package auction.zad1

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global._
import com.typesafe.config._

import auction.zad1.Messages._
import auction.zad1.Util._

object AuctionSystem extends App {
    private val system: ActorSystem = ActorSystem("auctionsystem")
    val notifier = system.actorOf(Notifier(system.actorSelection(AuctionPublisher.PATH)), Notifier.LOCAL_NOTIFIER)
    val auctionSearch = system.actorOf(MasterSearch(), MasterSearch.MASTER_SEARCH_PATH)
    val querier = system.actorOf(Querier(50000, 10000))
    querier ! Querier.StartTests
    Await.result(system.whenTerminated, Duration.Inf)
}

object Querier {
    case object StartTests
    case object StartQuerying

    def apply(nAuctions: Int, nQueries: Int) = Props(new Querier(nAuctions, nQueries))
}

class Querier(var nAuctions: Int, var nQueries: Int) extends Actor with ActorLogging {
    import Querier._
    var createdAuctions = 0
    var queried = 0
    var received = 0
    var time0 = System.currentTimeMillis()
    override def receive: Receive = {
        case StartTests =>
            if (createdAuctions == nAuctions) {
                self ! StartQuerying
            } else {
                MasterSearch.getMasterSearch(context) ! AuctionCreated("" + System.currentTimeMillis())
                createdAuctions += 1
                self ! StartTests
            }
        case StartQuerying =>
            if (queried == nQueries) {
                println("sending took " + (System.currentTimeMillis() - time0) + " ms")
            } else {
                MasterSearch.getMasterSearch(context) ! AuctionQuery("1")
                queried += 1
                self ! StartQuerying
            }
        case AuctionQueryResult(m) =>
            if(m.size == 0) {
                println("SIZE 0 FOUND")
            }
            received += 1
            if(received == nQueries) {
                println("Took " + (System.currentTimeMillis() - time0) + " ms")
                context.system.terminate
            }
    }
}
