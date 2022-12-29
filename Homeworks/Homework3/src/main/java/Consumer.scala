import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, from_json}
import org.apache.spark.sql.types.{DoubleType, StructField, StructType}

object Consumer {
  def main(args: Array[String]): Unit = {

    val spark = SparkSession
      .builder()
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val rootLogger = Logger.getRootLogger
    println(rootLogger.getClass)
    rootLogger.setLevel(Level.WARN)

    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "health_data")
      .option("startingOffsets", "latest")
      .load()

    val schema: StructType = new StructType(Array(
      StructField("Diabetes_012", DoubleType, nullable = false),
      StructField("HighBP", DoubleType, nullable = false),
      StructField("HighChol", DoubleType, nullable = false),
      StructField("CholCheck", DoubleType, nullable = false),
      StructField("BMI", DoubleType, nullable = false),
      StructField("Stroke", DoubleType, nullable = false),
      StructField("HeartDiseaseorAttack", DoubleType, nullable = false),
      StructField("PhysActivity", DoubleType, nullable = false),
      StructField("Fruits", DoubleType, nullable = false),
      StructField("Veggies", DoubleType, nullable = false),
      StructField("HvyAlcoholConsump", DoubleType, nullable = false),
      StructField("AnyHealthcare", DoubleType, nullable = false),
      StructField("NoDocbcCost", DoubleType, nullable = false),
      StructField("GenHlth", DoubleType, nullable = false),
      StructField("MentHlth", DoubleType, nullable = false),
      StructField("PhysHlth", DoubleType, nullable = false),
      StructField("DiffWalk", DoubleType, nullable = false),
      StructField("Sex", DoubleType, nullable = false),
      StructField("Age", DoubleType, nullable = false),
      StructField("Education", DoubleType, nullable = false),
      StructField("Education", DoubleType, nullable = false),
      StructField("Education", DoubleType, nullable = false),
      StructField("Income", DoubleType, nullable = false),
      StructField("id", DoubleType, nullable = false)
    ))

    val df1 = df.selectExpr("CAST(value AS STRING)")

    val df2 = df1.select(from_json(col("value"), schema).as("data"))

    df2
      .writeStream
      .format("console")
      .outputMode("append")
      .start()
      .awaitTermination()

    spark.stop()
    spark.close()
  }
}
