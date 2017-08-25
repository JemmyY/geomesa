package org.locationtech.geomesa.convert.redis

import java.util.ServiceLoader

import com.typesafe.config.ConfigFactory
import org.locationtech.geomesa.convert.EnrichmentCacheFactory
import org.specs2.mutable.Specification
import redis.clients.jedis.Jedis

class MockRedis extends Jedis {
  var count = 0
  override def hgetAll(key: String): java.util.Map[String, String] = {
    import scala.collection.JavaConversions._
    if(count == 0) {
      count += 1
      Map("foo" -> "bar")
    } else {
      Map("foo" -> "baz")
    }
  }
}

class RedisEnrichmentCacheTest extends Specification {

  sequential

  "Redis enrichment cache" should {
    "work" >> {
      val connBuilder = new RedisConnectionBuilder {
        override def buildConnection(url: String): Jedis = new MockRedis
      }

      val cache = new RedisEnrichmentCache(connBuilder, "", -1)
      val res = cache.get(Array("1", "foo")).asInstanceOf[String]
      res must be equalTo "bar"
    }

    "respect timeouts" >> {
      val connBuilder = new RedisConnectionBuilder {
        override def buildConnection(url: String): Jedis = new MockRedis
      }

      val cache = new RedisEnrichmentCache(connBuilder, "", 1)
      val res = cache.get(Array("1", "foo")).asInstanceOf[String]
      res must be equalTo "bar"

      Thread.sleep(2)
      val res2 = cache.get(Array("1", "foo")).asInstanceOf[String]
      res2 must be equalTo "baz"
    }

    "load via SPI" >> {

      val conf = ConfigFactory.parseString(
        """
          |{
          |   type = "redis"
          |   redis-url = "foo"
          |   expiration = 10
          |}
        """.stripMargin
      )

      import scala.collection.JavaConversions._
      val cache = ServiceLoader.load(classOf[EnrichmentCacheFactory]).iterator().find(_.canProcess(conf)).map(_.build(conf))

      cache must not be None
    }

  }
}
