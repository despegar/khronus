/*
 * =========================================================================================
 * Copyright © 2015 the khronus project <https://github.com/hotels-tech/khronus>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package com.searchlight.khronus.util

import java.util.Map.Entry

import com.searchlight.khronus.model.{ CounterTimeWindow, HistogramTimeWindow, MetricType }
import com.typesafe.config.{ ConfigValue, ConfigObject, ConfigFactory }

import scala.collection.JavaConverters._
import scala.concurrent.duration.{ FiniteDuration, _ }

object Settings {

  val config = ConfigFactory.load()

  object Master {
    val TickCronExpression = config.getString("khronus.master.tick-expression")
    val CheckLeaderCronExpression = config.getString("khronus.master.check-leader-expression")
    val DiscoveryStartDelay = FiniteDuration(config.getDuration("khronus.master.discovery-start-delay", MILLISECONDS), MILLISECONDS)
    val DiscoveryInterval = FiniteDuration(config.getDuration("khronus.master.discovery-interval", MILLISECONDS), MILLISECONDS)
    val WorkerBatchSize = config.getInt("khronus.master.worker-batch-size")
    val MaxDelayBetweenClocks = FiniteDuration(config.getDuration("khronus.master.max-delay-between-clocks", MILLISECONDS), MILLISECONDS)
  }

  object Http {
    val Interface = config.getString("khronus.endpoint")
    val Port: Int = config.getInt("khronus.port")
  }

  object Window {
    val TickDelay = config.getInt("khronus.windows.tick-delay")
    val ConfiguredWindows = config.getDurationList("khronus.windows.durations", MILLISECONDS).asScala.map(adjustDuration(_))
    val RawDuration = 1 millis
    val WindowDurations = RawDuration +: ConfiguredWindows
  }

  object Dashboard {
    val MinResolutionPoints: Int = config.getInt("khronus.dashboards.min-resolution-points")
    val MaxResolutionPoints: Int = config.getInt("khronus.dashboards.max-resolution-points")
  }

  object InternalMetrics {
    val Enabled: Boolean = config.getBoolean("khronus.internal-metrics.enabled")
    val CheckOutliers: Boolean = config.getBoolean("khronus.internal-metrics.check-outliers")
  }

  object CassandraCluster {
    private val cassandraCfg = config.getConfig("khronus.cassandra.cluster").withFallback(sys.env("HOME") + "/sensitive.conf")
    val ClusterName = cassandraCfg.getString("name")
    val MaxConnectionsPerHost = cassandraCfg.getInt("max-connections-per-host")
    val SocketTimeout = cassandraCfg.getDuration("socket-timeout", MILLISECONDS).toInt
    val ConnectionTimeout = cassandraCfg.getDuration("connection-timeout", MILLISECONDS).toInt
    val Port = cassandraCfg.getInt("port")
    val Seeds = cassandraCfg.getString("seeds").split(",").toSeq
    val KeyspaceNameSuffix = cassandraCfg.getString("keyspace-name-suffix")
    val Username = cassandraCfg.getString("khronus.cassandra.username")
    val Password = cassandraCfg.getString("khronus.cassandra.password")
  }

  object CassandraMeta {
    private val cassandraCfg = config.getConfig("khronus.cassandra.meta")
    val ReplicationFactor = cassandraCfg.getInt("rf")
    val insertChunkSize = cassandraCfg.getInt("insert-chunk-size")
  }

  object CassandraBuckets {
    private val cassandraCfg = config.getConfig("khronus.cassandra.buckets")
    val ReplicationFactor = cassandraCfg.getInt("rf")
    val insertChunkSize = cassandraCfg.getInt("insert-chunk-size")
  }

  object CassandraSummaries {
    private val cassandraCfg = config.getConfig("khronus.cassandra.summaries")
    val ReplicationFactor = cassandraCfg.getInt("rf")
    val insertChunkSize = cassandraCfg.getInt("insert-chunk-size")
  }

  object CassandraLeaderElection {
    private val cassandraCfg = config.getConfig("khronus.cassandra.leader-election")
    val ReplicationFactor = cassandraCfg.getInt("rf")
  }

  object Histogram {
    private val histogramConfig = config.getConfig("khronus.histogram")
    val BucketRetentionPolicy = histogramConfig.getDuration("bucket-retention-policy", SECONDS).toInt
    val TimeWindows = Window.WindowDurations.sliding(2).map { dp ⇒
      val previous = dp.head
      val duration = dp.last
      HistogramTimeWindow(duration, previous, Window.WindowDurations.last != duration)
    }.toSeq

    val SummaryRetentionPolicyDefault = Duration(histogramConfig.getString("summary-retention-policy.default"))
    val SummaryRetentionPolicyOverrides: Map[Duration, Duration] = {
      val list: Iterable[ConfigObject] = histogramConfig.getObjectList("summary-retention-policy.overrides").asScala
      (for {
        item: ConfigObject ← list
        entry: Entry[String, ConfigValue] ← item.entrySet().asScala
        resolution = Duration(entry.getKey)
        ttl = Duration(entry.getValue.unwrapped().toString)
      } yield (resolution, ttl)).toMap
    }

    val SummaryRetentionPolicies = getRetentionPolicies(SummaryRetentionPolicyDefault, SummaryRetentionPolicyOverrides, Window.WindowDurations)

    val BucketLimit = histogramConfig.getInt("bucket-limit")
    val BucketFetchSize = histogramConfig.getInt("bucket-fetch-size")
    val SummaryLimit = histogramConfig.getInt("summary-limit")
    val SummaryFetchSize = histogramConfig.getInt("summary-fetch-size")

  }

  object Counter {
    private val counterConfig = config.getConfig("khronus.counter")
    val BucketRetentionPolicy = counterConfig.getDuration("bucket-retention-policy", SECONDS).toInt
    val SummaryRetentionPolicyDefault = Duration(counterConfig.getString("summary-retention-policy.default"))
    val SummaryRetentionPolicyOverrides: Map[Duration, Duration] = {
      val list: Iterable[ConfigObject] = counterConfig.getObjectList("summary-retention-policy.overrides").asScala
      (for {
        item: ConfigObject ← list
        entry: Entry[String, ConfigValue] ← item.entrySet().asScala
        resolution = Duration(entry.getKey)
        ttl = Duration(entry.getValue.unwrapped().toString)
      } yield (resolution, ttl)).toMap
    }
    val TimeWindows = Window.WindowDurations.sliding(2).map { dp ⇒
      val previous = dp.head
      val duration = dp.last
      CounterTimeWindow(duration, previous, Window.WindowDurations.last != duration)
    }.toSeq

    val SummaryRetentionPolicies = getRetentionPolicies(SummaryRetentionPolicyDefault, SummaryRetentionPolicyOverrides, Window.WindowDurations)

    val BucketLimit = counterConfig.getInt("bucket-limit")
    val BucketFetchSize = counterConfig.getInt("bucket-fetch-size")
    val SummaryLimit = counterConfig.getInt("summary-limit")
    val SummaryFetchSize = counterConfig.getInt("summary-fetch-size")
  }

  object BucketCache {
    private val bucketCacheConfig = config.getConfig("khronus.bucket-cache")

    val Enabled = bucketCacheConfig.getBoolean("enabled")

    val MaxStore = bucketCacheConfig.getInt("max-store")

    val MaxMetrics: Map[String, Int] = Map(MetricType.Timer -> bucketCacheConfig.getInt("max-metrics.timers"),
      MetricType.Counter -> bucketCacheConfig.getInt("max-metrics.counters"), MetricType.Gauge -> bucketCacheConfig.getInt("max-metrics.gauges"))

    def IsEnabledFor(metricType: String): Boolean = Option(bucketCacheConfig.getBoolean(metricType)).getOrElse(false)
  }

  private def adjustDuration(durationInMillis: Long): FiniteDuration = {
    durationInMillis match {
      case durationInMillis if durationInMillis < 1000 ⇒ durationInMillis millis
      case durationInMillis if durationInMillis < 60000 ⇒ (durationInMillis millis).toSeconds seconds
      case durationInMillis if durationInMillis < 3600000 ⇒ (durationInMillis millis).toMinutes minutes
      case _ ⇒ (durationInMillis millis).toHours hours
    }
  }

  private def getRetentionPolicies(default: Duration, overrides: Map[Duration, Duration], windows: Seq[Duration]): Map[Duration, Duration] = {
    windows.map(window ⇒ {
      if (overrides.contains(window)) {
        (window -> overrides(window))
      } else {
        (window -> default)
      }
    }).toMap
  }

}

