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

import auction.zad1.AuctionSearch
import auction.zad1.Messages._

class AuctionSearchSpec extends TestKit(ActorSystem("AuctionSearchSpec")) with WordSpecLike with BeforeAndAfterAll with ImplicitSender {
  override def afterAll(): Unit = {
    system.terminate
  }

  "AuctionSearch" must {
    "register new auction" in {
      import AuctionSearch._
      val auctionSearch = TestActorRef[AuctionSearch]
      auctionSearch ! AuctionCreated("title")
      assert (auctionSearch.underlyingActor.auctions contains "title")
    }
    "respond to right query" in {
      import AuctionSearch._
      val auctionSearch = system.actorOf(AuctionSearch())
      auctionSearch ! AuctionCreated("some random words")
      auctionSearch ! AuctionCreated("some other words")
      auctionSearch ! AuctionQuery("random")
      receiveOne(1 second) match {
        case AuctionQueryResult(map) =>
          assert (map contains "some random words")
          assert ((map contains "some other words") == false)
      }
    }
    "unregister auction" in {
      import AuctionSearch._
      val auctionSearch = TestActorRef[AuctionSearch]
      auctionSearch ! AuctionCreated("title")
      auctionSearch ! AuctionExpired
      assert (auctionSearch.underlyingActor.auctions isEmpty)
    }
  }
}
