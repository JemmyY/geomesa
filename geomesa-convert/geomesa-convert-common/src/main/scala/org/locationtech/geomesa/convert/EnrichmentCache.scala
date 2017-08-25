package org.locationtech.geomesa.convert

import java.util.ServiceLoader

import com.google.common.collect.Maps
import com.typesafe.config.Config

trait EnrichmentCache {

  def get(args: Array[String]): Any
  def put(args: Array[String], value: Any): Unit
  def clear(): Unit

}

trait EnrichmentCacheFactory {
  def canProcess(conf: Config): Boolean
  def build(conf: Config): EnrichmentCache
}

object EnrichmentCache {
  def apply(conf: Config): EnrichmentCache = {
    import scala.collection.JavaConversions._
    val fac = ServiceLoader.load(classOf[EnrichmentCacheFactory]).find(_.canProcess(conf)).getOrElse(throw new RuntimeException("Could not find applicable EnrichmentCache"))
    fac.build(conf)
  }
}

// For testing purposes
class SimpleEnrichmentCache(val cache: java.util.Map[String, java.util.HashMap[String, AnyRef]] = Maps.newHashMap[String, java.util.HashMap[String, AnyRef]]()) extends EnrichmentCache {

  override def get(args: Array[String]): Any = Option(cache.get(args(0))).map(_.get(args(1))).getOrElse(null)

  override def put(args: Array[String], value: Any): Unit = ???

  override def clear(): Unit = cache.clear()
}

class SimpleEnrichmentCacheFactory extends EnrichmentCacheFactory {
  override def canProcess(conf: Config): Boolean = conf.hasPath("type") && conf.getString("type").equals("simple")

  override def build(conf: Config): EnrichmentCache = new SimpleEnrichmentCache(conf.getConfig("data").root().unwrapped().asInstanceOf[java.util.Map[String, java.util.HashMap[String, AnyRef]]])
}