package dispatch.oauth
import Http.{%, q_str}

import collection.Map
import collection.immutable.{TreeMap, Map=>IMap}

import javax.crypto

import org.apache.http.protocol.HTTP.UTF_8
import org.apache.commons.codec.binary.Base64.encodeBase64
import org.apache.http.client.methods.HttpRequestBase

case class Consumer(key: String, secret: String)
case class Token(value: String, secret: String)
object Token {
  def apply(m: Map[String, String]): Token = Token(m("oauth_token"), m("oauth_token_secret"))
}

/** Import this object's methods to add signing operators to dispatch.Request */
object OAuth {
  implicit def Requst2RequestSigner(r: Request) = new RequestSigner(r)
  
  def sign(method: String, url: String, user_params: Map[String, Any], consumer: Consumer, 
      token: Option[Token], verifier: Option[String]) = {
    val oauth_params = TreeMap(
      "oauth_consumer_key" -> consumer.key,
      "oauth_signature_method" -> "HMAC-SHA1",
      "oauth_timestamp" -> (System.currentTimeMillis / 1000).toString,
      "oauth_nonce" -> System.nanoTime.toString
    ) ++ token.map { "oauth_token" -> _.value } ++ 
      verifier.map { "oauth_verifier" -> _ }
    
    val message = %%(method :: url :: q_str(oauth_params ++ user_params) :: Nil)
    
    val SHA1 = "HmacSHA1";
    val key_str = %%(consumer.secret :: (token map { _.secret } getOrElse "") :: Nil)
    val key = new crypto.spec.SecretKeySpec(key_str.getBytes(UTF_8), SHA1)
    val sig = {
      val mac = crypto.Mac.getInstance(SHA1)
      mac.init(key)
      new String(encodeBase64(mac.doFinal(bytes(message))))
    }
    oauth_params + ("oauth_signature" -> sig)
  }
  
  private def %% (s: Seq[String]) = s map % mkString "&"
  private def bytes(str: String) = str.getBytes(UTF_8)

  class RequestSigner(r: Request) {
    // sign requests with an Authorization header
    def <@ (consumer: Consumer): Request = sign(consumer, None, None)
    def <@ (consumer: Consumer, token: Token): Request = sign(consumer, Some(token), None)
    def <@ (consumer: Consumer, token: Token, verifier: String): Request = 
      sign(consumer, Some(token), Some(verifier))
    
    /** Sign request using Post (<<) parameters and query string */
    private def sign (consumer: Consumer, token: Option[Token], verifier: Option[String]) = 
      r next { req =>
        val oauth_url = Http.to_uri(r.host, req).toString.split('?')(0)
        val query_map = split_decode(req.getURI.getRawQuery)
        val oauth_params = OAuth.sign(req.getMethod, oauth_url, query_map ++ (req match {
          case before: Post => before.values
          case _ => IMap()
        }), consumer, token, verifier)
        req.addHeader("Authorization", "OAuth " + oauth_params.map { case (k, v) =>
          (Http % k) + "=\"%s\"".format(Http % v)
        }.mkString(",") )
        req
      }
    def >% [T] (block: IMap[String, String] => T) = r >- ( split_decode andThen block )
    def as_token = r >% { Token(_) }
    
    val split_decode: (String => IMap[String, String]) = {
      case null => IMap.empty
      case query => IMap.empty ++ query.split('&').map { nvp =>
        ( nvp split "=" map Http.-% ) match { 
          case Seq(name, value) => name -> value
          case Seq(name) => name -> ""
        }
      }
    }
  }
  
}
