package com.coralogix.zio.k8s.examples.leader

import com.coralogix.zio.k8s.client._
import com.coralogix.zio.k8s.client.config.backend.K8sBackend
import com.coralogix.zio.k8s.client.impl.{ ResourceClient, ResourceStatusClient }
import com.coralogix.zio.k8s.client.model._
import com.coralogix.zio.k8s.model.pkg.apis.meta.v1.{ ObjectMeta, Status }
import io.circe.Codec
import io.circe.generic.semiauto._
import zio._
import zio.prelude.data.Optional

// Example of defining a custom resource client without using zio-k8s-crd

case class Crontab(
  metadata: Optional[ObjectMeta],
  status: Optional[CrontabStatus],
  spec: String,
  image: String,
  replicas: Int
)

object Crontab {
  implicit val crontabCodec: Codec[Crontab] = deriveCodec

  implicit val metadata: ResourceMetadata[Crontab] = new ResourceMetadata[Crontab] {
    override def kind: String = "CronTab"
    override def apiVersion: String = "apiextensions.k8s.io/v1"
    override def resourceType: model.K8sResourceType =
      K8sResourceType("crontabs", "apiextensions.k8s.io", "v1")
  }

  implicit val k8sObject: K8sObject[Crontab] =
    new K8sObject[Crontab] {
      override def metadata(obj: Crontab): Optional[ObjectMeta] = obj.metadata

      override def mapMetadata(f: ObjectMeta => ObjectMeta)(r: Crontab): Crontab =
        r.copy(metadata = r.metadata.map(f))
    }

  implicit val k8sObjectStatus: K8sObjectStatus[Crontab, CrontabStatus] =
    new K8sObjectStatus[Crontab, CrontabStatus] {
      override def status(obj: Crontab): Optional[CrontabStatus] = obj.status

      override def mapStatus(f: CrontabStatus => CrontabStatus)(obj: Crontab): Crontab =
        obj.copy(status = obj.status.map(f))
    }
}

case class CrontabStatus(replicas: Int, labelSelector: String)

object CrontabStatus {
  implicit val crontabStatusCodec: Codec[CrontabStatus] = deriveCodec
}

package object crontabs {
  type Crontabs = Crontabs.Service

  object Crontabs {
    type Generic = NamespacedResource[Crontab] with NamespacedResourceStatus[CrontabStatus, Crontab]

    trait Service
        extends NamespacedResource[Crontab] with NamespacedResourceStatus[CrontabStatus, Crontab]

    class Live(
      override val asGenericResource: ResourceClient[Crontab, Status],
      override val asGenericResourceStatus: ResourceStatusClient[CrontabStatus, Crontab]
    ) extends Service

    val live: ZLayer[K8sCluster with K8sBackend, Nothing, Crontabs] =
      ZLayer {
        for {
          backend <- ZIO.service[K8sBackend]
          cluster <- ZIO.service[K8sCluster]
        } yield {
          val client =
            new ResourceClient[Crontab, Status](Crontab.metadata.resourceType, cluster, backend)
          val statusClient = new ResourceStatusClient[CrontabStatus, Crontab](
            Crontab.metadata.resourceType,
            cluster,
            backend
          )
          new Live(client, statusClient)
        }
      }
  }
}
