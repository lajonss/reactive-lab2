package auction.zad2

import akka.testkit.TestKit
import akka.actor.ActorSystem
import akka.actor.ActorRef
import org.scalatest.WordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import akka.testkit.TestFSMRef
import akka.testkit.ImplicitSender
import scala.concurrent.duration._

import auction.zad1.Auction
import auction.zad1.AuctionSearch
import auction.zad1.Buyer
import auction.zad1.Messages._

class BidToppingStrategySpecs extends TestKit(ActorSystem("BidToppingStrategySpec")) with WordSpecLike with BeforeAndAfterAll with ImplicitSender {
  override def beforeAll(): Unit = {
    system.actorOf(AuctionSearch(), AuctionSearch.AUCTION_SEARCH_PATH)
  }
  override def afterAll(): Unit = {
    system.terminate
  }

  "Auction" must {
    "notify previous bidder about his bid being topped" in {
      val seller = TestProbe()
      val auction = system.actorOf(Auction(10, 5 second, 5 second, "title", seller.ref))
      val buyer1 = TestProbe()
      val buyer2 = TestProbe()
      buyer1.send(auction, Bid(11))
      buyer2.send(auction, Bid(12))
      buyer1.expectMsg(BidTopped(11, 12))
    }
  }

  "Buyer" must {
    "try to increase bid" in {
      val buyer = TestActorRef(new Buyer(10, "andrzej"))
      buyer ! BidTopped(1, 2)
      receiveOne(3 seconds) match {
        case Bid(n: Int) =>
          assert (n > 1)
      }
    }
    // "not take credits" in {
    //   val buyer = TestActorRef(new Buyer(0, "andrzej"))
    //   buyer ! BidTopped(0, 2)
    //   receiveOne(10 seconds) match {
    //     case Bid(n: Int) =>
    //       assert (n <= 0)
    //     case x: Any => println(x)
    //   }
    // }
  }
}
