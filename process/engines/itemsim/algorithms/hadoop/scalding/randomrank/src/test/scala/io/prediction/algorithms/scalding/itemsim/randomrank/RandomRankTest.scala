package io.prediction.algorithms.scalding.itemsim.randomrank

import org.specs2.mutable._

import com.twitter.scalding._

import io.prediction.commons.scalding.appdata.Items
import io.prediction.commons.scalding.modeldata.{ItemSimScores}
import io.prediction.commons.filepath.{AlgoFile}

class RandomRankTest extends Specification with TupleConversions {
  def test(
    algoid: Int,
    modelSet: Boolean,
    itypes: List[String],
    numSimilarItems: Int,
    items: List[(String, String)],
    itemSimScores: List[(String, String, String, String, Int, Boolean)]) = {
    val training_dbType = "file"
    val training_dbName = "testpath/"

    val modeldata_dbType = "file"
    val modeldata_dbName = "testpath/"

    val hdfsRoot = "testpath/"

    val appid = 7
    val engineid = 10
    val evalid = None

    JobTest("io.prediction.algorithms.scalding.itemsim.randomrank.RandomRank")
      .arg("training_dbType", training_dbType)
      .arg("training_dbName", training_dbName)
      .arg("modeldata_dbType", modeldata_dbType)
      .arg("modeldata_dbName", modeldata_dbName)
      .arg("hdfsRoot", hdfsRoot)
      .arg("appid", appid.toString)
      .arg("engineid", engineid.toString)
      .arg("algoid", algoid.toString)
      .arg("itypes", itypes)
      .arg("numSimilarItems", numSimilarItems.toString)
      .arg("modelSet", modelSet.toString)
      .source(Items(appId=appid, itypes=Some(itypes), dbType=training_dbType, dbName=training_dbName, dbHost=None, dbPort=None).getSource, items)
      .sink[(String, String, String, String, Int, Boolean)](ItemSimScores(dbType=modeldata_dbType, dbName=modeldata_dbName, dbHost=None, dbPort=None, algoid=algoid, modelset=modelSet).getSource) { outputBuffer =>

        def takeOutScores(d: List[(String, String, String, String, Int, Boolean)]) = {
          // don't check score and itypes.
          // for iids, don't check order. convert to set
          d map {x => (x._1, x._2.split(",").toSet, x._5, x._6) }
        }

        def getScoresOnly(d: List[(String, String, String, String, Int, Boolean)]) = {
          d flatMap {x => x._3.split(",").toList.map(_.toDouble)}
        }

        def getIids(d: List[(String, String, String, String, Int, Boolean)]) = {
          // List(List("i0", "i1"), List("i1", "i0"), ...)

          d map { x => x._2.split(",").toList }
        }

        def getItypes(d: List[(String, String, String, String, Int, Boolean)]) = {
          //("i0", "i2,i3", "0.0,0.0", "[t1,t2,t3],[t2,t3]", algoid, modelSet),
          // => List( List( (i2, List(t1,t2,t3)) , (i3, List(t2,t3)) )
          d.map { x =>
            val itypesList = x._4.split("],").toList.map(x => x.stripPrefix("[").stripSuffix("]").split(",").toList)
            val iidList = x._2.split(",").toList

            iidList zip itypesList
          }
        }

        "generate correct user and item pairs in modeldata" in {

          // don't check scores since they are random
          val result = takeOutScores(outputBuffer.toList)
          val expected = takeOutScores(itemSimScores)

          result must containTheSameElementsAs(expected)

        }

        "generate different scores for each pair in modeldata" in {
          // very simple way to check if the scores are random
          // (just to check if they are different)

          val scoresList: List[Double] = getScoresOnly(outputBuffer.toList)
          val scoresSet = scoresList.toSet

          scoresSet.size must be_==(scoresList.size)

        }

        "not generate same order of iid for all iid group" in {

          getIids(outputBuffer.toList).toSet.size must be_>(1)

        }

        "itypes order match the iids order" in {

          // extract (iid, itypes) from the output
          val itypesList = getItypes(outputBuffer.toList)
          val itemsMap = items.toMap

          // use the iid only and contruct the (iid, itypes)
          val expected = getIids(outputBuffer.toList).map(x =>
            // x is List of iid
            // create the List of item types using the iid
            x.map(x => (x, itemsMap(x).split(",").toList))
          )

          itypesList must be_==(expected)
        }

      }
      .run
      .finish
  }

  "randomrank.RandomRank with selected itypes" should {
    val algoid = 12
    val modelSet = false
    val itypes = List("t1", "t2")
    val items = List(("i0", "t1,t2,t3"), ("i1", "t2,t3"), ("i2", "t4"), ("i3", "t3,t4"))
    val itemSimScores = List(
      ("i1", "i0", "0.0", "[t1,t2,t3]", algoid, modelSet),
      ("i0", "i1", "0.0", "[t2,t3]", algoid, modelSet))

    test(algoid, modelSet, itypes, 500, items, itemSimScores)
  }

  "randomrank.RandomRank with all itypes" should {
    val algoid = 12
    val modelSet = false
    val itypes = List("")
    val items = List(("i0", "t1,t2,t3"), ("i1", "t2,t3"), ("i2", "t4"), ("i3", "t3,t4"))
    val itemSimScores = List(
      ("i3", "i0,i1,i2", "0.0,0.0,0.0", "[t1,t2,t3],[t2,t3],[t4]", algoid, modelSet),      
      ("i2", "i0,i1,i3", "0.0,0.0,0.0", "[t1,t2,t3],[t2,t3],[t3,t4]", algoid, modelSet),
      ("i1", "i0,i2,i3", "0.0,0.0,0.0", "[t1,t2,t3],[t4],[t3,t4]", algoid, modelSet),
      ("i0", "i1,i2,i3", "0.0,0.0,0.0", "[t2,t3],[t4],[t3,t4]", algoid, modelSet))

    test(algoid, modelSet, itypes, 500, items, itemSimScores)
  }
  // TODO: test with smaller number of numRecommendations (but can't know expected result beacause the score is random...)
}
