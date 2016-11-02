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

import auction.zad1.Seller
import auction.zad1.AuctionSearch
import auction.zad1.Messages._

class SellerSpec extends TestKit(ActorSystem("SellerSpec")) with WordSpecLike with BeforeAndAfterAll with ImplicitSender {
  override def beforeAll(): Unit = {
    system.actorOf(AuctionSearch(), AuctionSearch.AUCTION_SEARCH_PATH)
  }

  override def afterAll(): Unit = {
    system.terminate
  }

  "Seller" must {
    "react to sold auction" in {
      val seller = TestActorRef(new Seller(0))
      val buyer = TestProbe()
      seller ! AuctionSold(buyer.ref, 0)
      assert (seller.underlyingActor.auctionsQuantity == -1)
    }
    "react to expired auction" in {
      val seller = TestActorRef(new Seller(0))
      val buyer = TestProbe()
      seller ! AuctionExpired
      assert (seller.underlyingActor.auctionsQuantity == -1)
    }
  }
}
