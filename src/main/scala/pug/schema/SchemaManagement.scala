/*
 * Copyright 2020 Pugilistic Codeworks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pug.schema

import cats.implicits._
import doobie._
import doobie.implicits._
import org.typelevel.log4cats._
import org.typelevel.log4cats.slf4j._

class SchemaManagement(schema: Option[String] = None) {
  protected val log: SelfAwareStructuredLogger[ConnectionIO] = LoggerFactory[ConnectionIO].getLogger

  def schemaMetadataTableName = "schema_metadata"

  val createSchemaMetadataTable =
    fr"""CREATE TABLE IF NOT EXISTS""" ++ Fragment.const(schemaMetadataTableName) ++ fr"""(
        component   VARCHAR PRIMARY KEY,
        version     INTEGER NOT NULL,
        min_compat  INTEGER NOT NULL,
        checksum    VARCHAR
    )"""

  protected def booleanCount(desc: String)(results: Seq[Int]): Boolean = results match {
    case Seq(0) => false
    case Seq(1) => true
    case x      => throw new Exception(s"Expected 0 or 1 entries for $desc, found $x")
  }

  protected def schemaExists(schemaName: String): ConnectionIO[Boolean] =
    sql" select count(*) from information_schema.schemata where schema_name = $schemaName"
      .query[Int]
      .to[Seq] map booleanCount(s"$schemaName in information_schema.schemata")

  protected def tableExistsInAnySchema(tableName: String): ConnectionIO[Boolean] =
    sql"select count(*) from information_schema.tables where table_name = $tableName"
      .query[Int]
      .to[Seq] map booleanCount(s"$tableName in information_schema.tables")

  protected def tableExistsInSchema(schemaName: String, tableName: String): ConnectionIO[Boolean] =
    sql"select count(*) from information_schema.tables where table_schema = $schemaName and table_name = $tableName"
      .query[Int]
      .to[Seq] map booleanCount(s"$schemaName.$tableName in information_schema.tables")

  protected def createSchemaIfNecessary(schemaOpt: Option[String]): ConnectionIO[Unit] = schemaOpt match {
    case None => ().pure[ConnectionIO]

    case Some(schemaName) =>
      schemaExists(schemaName).flatMap {
        case true  => ().pure[ConnectionIO]
        case false => sql"create schema if not exists #$schemaName".update.run.map(_ => ())
      }
  }

  protected def initializeSchemaManagement(): ConnectionIO[Unit] = {
    for {
      // Create the schema if requested, and does not yet exist
      _ <- createSchemaIfNecessary(schema)

      // See if the metadata table exists
      exists <- schema match {
        case None             => tableExistsInAnySchema(schemaMetadataTableName)
        case Some(schemaName) => tableExistsInSchema(schemaName, schemaMetadataTableName)
      }

      // Create the metadata table if necessary
      _ <-
        if (exists) ().pure[ConnectionIO]
        else {
          log.info("Initializing schema metadata tables") *>
            createSchemaMetadataTable.update.run
        }
    } yield ()
  }

  def getComponentVersion(componentName: String): ConnectionIO[Option[Int]] =
    (fr"SELECT version from" ++ Fragment.const(schemaMetadataTableName) ++ fr"""
          WHERE component = $componentName
    """).query[Int].option

  def getComponentVersion(component: SchemaComponent): ConnectionIO[Option[Int]] =
    getComponentVersion(component.component)

  protected def updateComponentVersion(componentName: String, ver: Int): ConnectionIO[Boolean] =
    (fr"UPDATE" ++ Fragment.const(schemaMetadataTableName) ++ fr"""
          SET version = $ver
          WHERE component = $componentName
    """).update.run.map(_ > 0)

  def initializeComponent(component: SchemaComponent): ConnectionIO[Unit] = {
    val name = component.component
    val checksum = component.currentChecksum()
    val checksumStr = checksum.getOrElse("<none>")
    val currentVersion = component.currentVersion
    (fr"""
      SELECT version, min_compat, checksum
      FROM""" ++ Fragment.const(schemaMetadataTableName) ++ fr"""
      WHERE component = $name
      FOR UPDATE
      """)
      .query[(Int, Int, Option[String])]
      .option
      .flatMap {
        case None =>
          for {
            _ <- log.info(s"$name schema not present, creating version $currentVersion (checksum $checksumStr)...")
            _ <- createSchemaIfNecessary(component.dbSchemaName)
            _ <- component.executeStep(component.currentSchema)
            _ <- (fr"""
                 INSERT INTO """ ++ Fragment.const(schemaMetadataTableName) ++ fr"""
                   (component, version, min_compat, checksum)
                   VALUES ($name, $currentVersion, ${component.minCompat}, $checksum)
              """).update.run
          } yield ()

        case Some((dbVer, minCompat, dbChecksumOpt)) =>
          if (dbVer == currentVersion) {
            val name = component.component
            (checksum, dbChecksumOpt) match {
              case (Some(codeChecksum), Some(dbChecksum)) =>
                if (dbChecksum == codeChecksum) {
                  log.info(s"$name schema is current at version $dbVer (checksum $codeChecksum)")
                } else {
                  new Exception(
                    s"$name schema version $dbVer has checksum $dbChecksum in db, expected to be $codeChecksum"
                  )
                    .raiseError[ConnectionIO, Unit]
                }

              case (_, None) =>
                log.warn(
                  s"$name schema version $currentVersion has checksum $checksumStr, but no checksum in db, allowing to continue"
                )

              case (None, Some(_)) =>
                log.warn(
                  s"$name schema version $currentVersion has no checksum, but checksum in db, allowing to continue"
                )
            }
          } else if (currentVersion < dbVer) {
            if (currentVersion < minCompat) {
              val e = new Exception(
                s"$name schema version $currentVersion is not compatible with current schema (min compatible version is $minCompat)"
              )
              log.error(s"$name schema validation failed: ${e.getMessage}") *>
                e.raiseError[ConnectionIO, Unit]
            } else
              log.warn(
                s"$name schema compatible, allowing to continue (db = $dbVer, min compat = $minCompat, app = $currentVersion)"
              )
          } else {
            component
              .migrateToCurrent(dbVer)
              .flatMap(v => updateComponentVersion(component.component, v))
              .as(())
          }
      }
  }

  def bootstrap(components: SchemaComponent*): ConnectionIO[Unit] = {
    for {
      _ <- initializeSchemaManagement()
      _ <- components.map(initializeComponent).sequence
    } yield ()
  }
}
