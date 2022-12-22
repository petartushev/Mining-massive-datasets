import org.apache.spark.sql.catalyst.encoders.{ExpressionEncoder, RowEncoder}
import org.apache.spark.sql.types.{IntegerType, LongType, StructField, StructType}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.{DataFrame, Encoder, Row, SparkSession}
import org.apache.spark.ml.recommendation.ALS
import org.apache.spark.ml.evaluation.RegressionEvaluator
//import org.apache.spark.impl

case class Rating(userId: Int, movieId: Int, rating: Int, timestamp: Long){

}


object Main {


  def main(args: Array[String]): Unit = {

    val spark = SparkSession
      .builder()
      .appName("Alternating least squares")
      .master("local[*]")
      .getOrCreate()

    def parseRating(str: String): Rating = {
      val fields = str.split("\\t")
      new Rating(fields(0).toInt, fields(1).toInt, fields(2).toInt, fields(3).toLong)
    }


    import spark.implicits._

    val dataFrame: DataFrame = spark.read.text("./ml-100k/u1.base")
      .map(row => parseRating(row.mkString))
      .toDF() //"userId", "movieId", "rating", "timestamp"

    val df = dataFrame.drop("timestamp")

    println(df.describe().show())

//    df1.show(5, false)


    val Array(train, test) = df.randomSplit(Array(.8, .2))

//    println()

    val als = new ALS()
      .setMaxIter(5)
      .setRegParam(.01)
      .setNonnegative(true)
      .setImplicitPrefs(false)
      .setColdStartStrategy("drop")
      .setUserCol("userId")
      .setItemCol("movieId")
      .setRatingCol("rating")

    val model = als.fit(train)

    val preds = model.transform(test)

    val evaluator = new RegressionEvaluator()
      .setMetricName("rmse")
      .setLabelCol("rating")
      .setPredictionCol("prediction")

    val rmse = evaluator.evaluate(preds)
    println(s"Root-mean-square error = $rmse")

    val userRecs = model.recommendForAllUsers(10)
    val movieRecs = model.recommendForAllItems(10)

//    println("Top ten movie recommendations for each user: ")
//    println(userRecs + "\n")
//    println("Top ten user recommendations for each movie: ")
//    println(movieRecs + "\n")

//    println(userRecs.show())
//    println()
    println(movieRecs.select("recommendations").show())
  }
}