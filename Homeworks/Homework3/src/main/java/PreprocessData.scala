import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.expressions.Window
import org.apache.spark.sql.functions.{lit, row_number}
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.{DataFrame, Dataset, Encoder, Row, SparkSession}
import org.apache.spark.sql.Encoders
import scala.Console.println
import scala.collection.immutable.Nil.distinct


object PreprocessData {
  def main(args: Array[String]): Unit = {

    val spark: SparkSession = SparkSession
      .builder()
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val rootLogger: Logger = Logger.getRootLogger
    rootLogger.setLevel(Level.WARN)

    val raw_df: DataFrame = spark
      .read
      .option("header", "true")
      .csv("./data/raw/diabetes_012_health_indicators_BRFSS2015.csv")
      .toDF()

    //Smoker, HighBP, HighChol

    val df: DataFrame = raw_df.select(raw_df.columns.map(c => col(c).cast("Double")) : _*)

    val df1: DataFrame= df.withColumn("id", row_number().over(Window.orderBy(lit(null))) - 1)

    val train_typeless = df1.stat.sampleBy("Smoker", Map(0.0 -> 0.8, 1.0 -> 0.8), 0)

    val train: DataFrame = train_typeless.select(df1.columns.map(c => col(c).cast("Double")) : _*)

    val leftAnti = df1.join(train, df1("id") === train("id"), "leftanti")

    leftAnti.join(train, df1("id") === train("id"), "inner").take(1)

    train
      .write
      .option("header", "true")
      .option("delimiter", ",")
      .csv("./data/smoker/offline_stratified_80")

    leftAnti
      .write
      .option("header", "true")
      .option("delimiter", ",")
      .csv("./data/smoker/online_stratified_20")

    spark.stop()






  }
}