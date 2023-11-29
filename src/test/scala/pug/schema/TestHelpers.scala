package pug.schema

import scala.util.Random

import cats.effect.IO
import doobie.Transactor

object TestHelpers {
  def testTransactor(name: String, random: Boolean = false) = {
    val suffix = if (!random) "" else "-" + (1 to 16).map(_ => Random.nextPrintableChar()).mkString
    val db = s"$name$suffix"
    Transactor.fromDriverManager[IO](
      "org.h2.Driver",
      s"jdbc:h2:mem:$db;USER=sa;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
    )
  }
}
