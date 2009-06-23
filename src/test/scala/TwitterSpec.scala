import org.scalatest.Spec
import org.scalatest.matchers.ShouldMatchers

class TwitterSpec extends Spec with ShouldMatchers {
  import dispatch._
  import json.Js._
  import twitter._
  
  describe("Twitter Search") {
    val http = new Http
    it("should find tweets containing #dbDispatch") {
      val res = http(Search("#dbDispatch").results)
      res.isEmpty should be (false)
      res map Status.text forall { _ contains "#dbDispatch" } should be (true)
    }
  }
}
