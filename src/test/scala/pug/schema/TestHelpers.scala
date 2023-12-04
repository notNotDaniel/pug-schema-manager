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

import scala.util.Random

import cats.effect.IO
import doobie.Transactor

object TestHelpers {
  def testTransactor(name: String, random: Boolean = false) = {
    val suffix = if (!random) "" else "-" + Random.alphanumeric.take(16).mkString
    val db = s"$name$suffix"
    Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      s"jdbc:h2:mem:$db;USER=sa;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
    )
  }
}
