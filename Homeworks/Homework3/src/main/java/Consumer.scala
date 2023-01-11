import breeze.storage.ConfigurableDefault.fromV
import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkConf, SparkEnv}
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.sql.{Column, DataFrame, SparkSession}
import org.apache.spark.sql.functions.{col, explode, from_json, get_json_object, lit, split, udf}
import org.apache.spark.sql.types.{DoubleType, StringType, StructField, StructType}
//import org.apache.spark.sql.types._

case class Input(Diabetes_012: Double,
                 HighBP: Double,
                 HighChol: Double,
                 CholCheck: Double,
                 BMI: Double,
                 Stroke: Double,
                 HeartDiseaseorAttack: Double,
                 PhysActivity: Double,
                 Fruits: Double,
                 Veggies: Double,
                 HvyAlcoholConsump: Double,
                 AnyHealthcare: Double,
                 NoDocbcCost: Double,
                 GenHlth: Double,
                 MentHlth: Double,
                 PhysHlth: Double,
                 DiffWalk: Double,
                 Sex: Double,
                 Age: Double,
                 Education: Double,
                 Income: Double,
                 id: Double)

object Consumer {

  def main(args: Array[String]): Unit = {

    def parseInput(a: Array[String]): Input = {
      Input(
        a(0).toDouble,
        a(1).toDouble,
        a(2).toDouble,
        a(3).toDouble,
        a(4).toDouble,
        a(5).toDouble,
        a(6).toDouble,
        a(7).toDouble,
        a(8).toDouble,
        a(9).toDouble,
        a(10).toDouble,
        a(11).toDouble,
        a(12).toDouble,
        a(13).toDouble,
        a(14).toDouble,
        a(15).toDouble,
        a(16).toDouble,
        a(17).toDouble,
        a(18).toDouble,
        a(19).toDouble,
        a(20).toDouble,
        a(21).toDouble)
    }

    val spark = SparkSession
      .builder()
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val rootLogger = Logger.getRootLogger
    println(rootLogger.getClass)
    rootLogger.setLevel(Level.WARN)

    val clf: PipelineModel = PipelineModel.read.load("/home/petar/Fakultet/Semester 7/Mining massive datasets/Homeworks/Homework3/models/GBClassifier/")

    var df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "health_data")
      .option("startingOffsets", "latest")
      .load()

    val schema: StructType = new StructType(Array(
      StructField("Diabetes_012", StringType, nullable = false),
      StructField("HighBP", StringType, nullable = false),
      StructField("HighChol", StringType, nullable = false),
      StructField("CholCheck", StringType, nullable = false),
      StructField("BMI", StringType, nullable = false),
      StructField("Stroke", StringType, nullable = false),
      StructField("HeartDiseaseorAttack", StringType, nullable = false),
      StructField("PhysActivity", StringType, nullable = false),
      StructField("Fruits", StringType, nullable = false),
      StructField("Veggies", StringType, nullable = false),
      StructField("HvyAlcoholConsump", StringType, nullable = false),
      StructField("AnyHealthcare", StringType, nullable = false),
      StructField("NoDocbcCost", StringType, nullable = false),
      StructField("GenHlth", StringType, nullable = false),
      StructField("MentHlth", StringType, nullable = false),
      StructField("PhysHlth", StringType, nullable = false),
      StructField("DiffWalk", StringType, nullable = false),
      StructField("Sex", StringType, nullable = false),
      StructField("Age", StringType, nullable = false),
      StructField("Education", StringType, nullable = false),
      StructField("Income", StringType, nullable = false),
      StructField("id", StringType, nullable = false)
    ))

    df = df.selectExpr("CAST(value AS STRING)")

    df = df.select(from_json(col("value"), schema).as("data"))

    val df1 = df
      .map(row =>
        parseInput(row.mkString(",").replaceAll("[\\[\\]]", "").split(",")))
      .toDF()

    df1.printSchema()

    val preds = clf.transform(df1)

    val preds1 = preds.select("prediction")

    preds1.printSchema()

    preds1
      .toJSON
      .writeStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("topic", "health_data_predicted")
      .option("checkpointLocation", "/home/petar/Fakultet/Semester 7/Mining massive datasets/Homeworks/Homework3/data/temp/")
      .start()
      .awaitTermination()

    spark.stop()
    spark.close()
  }
}
