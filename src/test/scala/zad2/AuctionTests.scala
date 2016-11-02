package auction.zad2

import akka.testkit.TestKit
import akka.actor.ActorSystem
import akka.actor.ActorRef
import org.scalatest.WordSpecLike
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import akka.testkit.ImplicitSender
import scala.concurrent.duration._
import akka.testkit.TestFSMRef

import auction.zad1.Auction
import auction.zad1.AuctionSearch
import auction.zad1.Messages._

class AuctionSpec extends TestKit(ActorSystem("AuctionSpec")) with WordSpecLike with BeforeAndAfterAll with ImplicitSender {
  override def beforeAll(): Unit = {
    system.actorOf(AuctionSearch(), AuctionSearch.AUCTION_SEARCH_PATH)
  }

  override def afterAll(): Unit = {
    system.terminate
  }

  "Auction" must {
    "expire in given time" in {
      val seller = TestProbe()
      val auction = TestFSMRef(new Auction(10, 1 second, 1 second, "title", seller.ref))
      seller.expectMsg(10 seconds, AuctionExpired)
    }
    "not allow bids lower than starting price" in {
      val seller = TestProbe()
      val auction = TestFSMRef(new Auction(10, 5 second, 5 second, "title", seller.ref))
      auction ! Bid(9)
      expectMsg(BidTooLow(9))
      assert (auction.stateName == Auction.Created)
    }
    "not allow bids lower than previous ones" in {
      val seller = TestProbe()
      val auction = TestFSMRef(new Auction(10, 5 second, 5 second, "title", seller.ref))
      val buyer = TestProbe()
      buyer.send(auction, Bid(12))
      auction ! Bid(11)
      expectMsg(BidTooLow(11))
      assert (auction.stateData == Auction.Data(buyer.ref, 12))
    }
    "notify the winner" in {
      val seller = TestProbe()
      val auction = TestFSMRef(new Auction(10, 1 second, 1 second, "title", seller.ref))
      auction ! Bid(11)
      expectMsg(WonAuction(11))
    }
    "notify seller about being sold" in {
      val seller = TestProbe()
      val auction = TestFSMRef(new Auction(10, 1 second, 1 second, "title", seller.ref))
      val buyer = TestProbe()
      buyer.send(auction, Bid(11))
      seller.expectMsg(AuctionSold(buyer.ref, 11))
    }
  }
}
