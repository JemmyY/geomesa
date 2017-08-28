/***********************************************************************
 * Copyright (c) 2013-2017 Commonwealth Computer Research, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution and is available at
 * http://www.opensource.org/licenses/apache2.0.php.
 ***********************************************************************/


package org.locationtech.geomesa.convert.redis

import java.util
import java.util.concurrent.TimeUnit

import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.typesafe.config.Config
import org.apache.commons.pool2.impl.{DefaultPooledObject, GenericKeyedObjectPool}
import org.apache.commons.pool2.{BaseKeyedPooledObjectFactory, KeyedPooledObjectFactory, PooledObject}
import org.locationtech.geomesa.convert.{EnrichmentCache, EnrichmentCacheFactory}
import redis.clients.jedis.Jedis

trait RedisConnectionBuilder {
  def buildConnection(url: String): Jedis
}

class RedisEnrichmentCache(connBuilder: RedisConnectionBuilder, url: String, expiration: Long = -1) extends EnrichmentCache {
  private val fac: KeyedPooledObjectFactory[String, Jedis] = new BaseKeyedPooledObjectFactory[String, Jedis] {
    override def create(key: String): Jedis = connBuilder.buildConnection(url)
    override def wrap(value: Jedis): PooledObject[Jedis] = new DefaultPooledObject[Jedis](value)
  }
  private val connPool = new GenericKeyedObjectPool[String,Jedis](fac)

  private var builder =
    if(expiration > 0) CacheBuilder.newBuilder().expireAfterWrite(expiration, TimeUnit.MILLISECONDS)
    else CacheBuilder.newBuilder()

  private val cache = builder
    .build(new CacheLoader[String, java.util.Map[String, String]] {
      override def load(k: String): util.Map[String, String] = {
        val conn = connPool.borrowObject(url)
        try {
          conn.hgetAll(k)
        } finally {
          connPool.returnObject(url, conn)
        }
      }
    })

  override def get(args: Array[String]): Any = cache.get(args(0)).get(args(1))
  override def put(args: Array[String], value: Any): Unit = ???
  override def clear(): Unit = ???
}

class RedisEnrichmentCacheFactory extends EnrichmentCacheFactory {
  override def canProcess(conf: Config): Boolean = conf.hasPath("type") && conf.getString("type").equals("redis")
  override def build(conf: Config): EnrichmentCache = {
    val url = conf.getString("redis-url")
    val timeout = if(conf.hasPath("expiration")) conf.getLong("expiration") else -1
    val connBuilder = new RedisConnectionBuilder {
      override def buildConnection(url: String): Jedis = new Jedis(url)
    }
    new RedisEnrichmentCache(connBuilder, url, timeout)
  }
}
