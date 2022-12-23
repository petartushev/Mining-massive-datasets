import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import org.apache.spark.ml.recommendation.ALS
import org.apache.spark.ml.evaluation.RegressionEvaluator
import org.mlflow.tracking.MlflowContext
import org.apache.log4j.{Level, Logger}

case class Rating(userId: Int, movieId: Int, rating: Int, timestamp: Long)

case class Person(userId: Int)


object Main {


  def main(args: Array[String]): Unit = {

    val experimentName: String = "Spark/Scala ALS recommendation"
    val MLFLOW_TRACKING_URI = "http://localhost:5000"

    val mlflowContext:MlflowContext = new MlflowContext(MLFLOW_TRACKING_URI).setExperimentId("1")
    val client = mlflowContext.getClient
    val experimentOpt = client.getExperimentByName(experimentName)

    if (!experimentOpt.isPresent){
      client.createExperiment(experimentName)
    }
    val run = mlflowContext.startRun("ALS train/eval")

    val maxIter: Int = 10
    val regParam: Float = .5f

    val spark = SparkSession
      .builder()
      .appName("Alternating least squares")
      .master("local[*]")
      .getOrCreate()

    val rootLogger: Logger = Logger.getRootLogger
    rootLogger.setLevel(Level.WARN)

    def parseRating(str: String): Rating = {
      val fields = str.split("\\t")
      Rating(fields(0).toInt, fields(1).toInt, fields(2).toInt, fields(3).toLong)
    }

    import spark.implicits._

    val dataFrame: DataFrame = spark.read.text("./ml-100k/u1.base")
      .map(row => parseRating(row.mkString))
      .toDF()

    val df = dataFrame.drop("timestamp")

    val Array(train, test) = df.randomSplit(Array(.8, .2))

    run.logParam("maxIter", maxIter.toString)
    run.logParam("regParam", regParam.toString)

    val als = new ALS()
      .setMaxIter(maxIter)
      .setRegParam(regParam)
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

    run.logMetric("RMSE", rmse)
    run.endRun()

    val user: Dataset[Person] = spark.createDataset(Seq(Person(688), Person(1), Person(2), Person(3), Person(4)))

    val recommendForSubsetDF = model.recommendForUserSubset(user, 5)

    recommendForSubsetDF.collect().foreach(println)

  }
}