import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.classification.GBTClassifier
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.feature.{StringIndexer, VectorAssembler}
import org.apache.spark.ml.tuning.{CrossValidator, ParamGridBuilder}
import org.apache.spark.mllib.evaluation.MulticlassMetrics
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.types.{DoubleType, StringType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SparkSession}

object GradientBoostedClassifier {
  def main(args: Array[String]): Unit = {

    val TRAIN = .8
    val MAX_ITER = 10
    val MAX_BINS = 10
    val MAX_DEPTH = 10

    val spark = SparkSession
      .builder()
      .master("local[*]")
      .getOrCreate()

    import spark.implicits._

    val rootLogger: Logger = Logger.getRootLogger
    rootLogger.setLevel(Level.WARN)

    val offline_typeless: DataFrame = spark
      .read
      .option("header", "true")
      .option("delimiter", ",")
      .csv("./data/smoker/offline_stratified_80")

    val offline: DataFrame = offline_typeless.select(offline_typeless.columns.map(col(_).cast("Double")) : _*)

    val assembler = new VectorAssembler()
      .setInputCols(offline.columns.filter(_ != "Smoker"))
      .setOutputCol("features")

    val labelIndexer = new StringIndexer()
      .setInputCol("Smoker")
      .setOutputCol("label")

    val Array(pipelineTrainData, pipelineTestData) = offline.randomSplit(Array(TRAIN, 1 - TRAIN))

    val clf = new GBTClassifier()
      .setLabelCol("label")
      .setFeaturesCol("features")
      .setMaxIter(MAX_ITER)
      .setMaxDepth(MAX_DEPTH)
      .setMaxBins(MAX_BINS)
      .setFeatureSubsetStrategy("auto")

    val stages = Array(assembler, labelIndexer, clf)

    val pipeline = new Pipeline().setStages(stages)

//    ----------------------------------  CROSS VALIDATION CODE ----------------------------------

    val paramGrid = new ParamGridBuilder()
      .addGrid(clf.maxBins, Array(25, 28)) //
      .addGrid(clf.maxIter, Array(5, 10, 15)) //
      .addGrid(clf.maxDepth, Array(5, 10, 15)) //
      .build()

    val evaluator = new BinaryClassificationEvaluator()
      .setLabelCol("label")
      .setMetricName("areaUnderROC")

    val cv = new CrossValidator()
      .setEstimator(pipeline)
      .setEvaluator(evaluator)
      .setEstimatorParamMaps(paramGrid)
      .setNumFolds(5)
      .setParallelism(4)

    val cvModel = cv.fit(pipelineTrainData)

    val predictionAndLabels: DataFrame = cvModel.transform(pipelineTestData)

    println(cvModel.getEstimatorParamMaps.zip(cvModel.avgMetrics).maxBy(_._2)._1)

//    ----------------------------------  CROSS VALIDATION CODE ----------------------------------


//    ----------------------------------  STANDARD CODE ----------------------------------

//    val pipelineModel = pipeline.fit(pipelineTrainData)
//
//    val preds = pipelineModel.transform(pipelineTestData)
//
//    val predictionAndLabels: DataFrame = preds.select("label", "prediction")

//    ----------------------------------  STANDARD CODE ----------------------------------

    val predictionAndLabelsRDD = predictionAndLabels
      .select("label", "prediction")
      .as[(Double, Double)]
      .rdd

    val metrics = new MulticlassMetrics(predictionAndLabelsRDD)
    val labels = metrics.labels

    val schema: StructType = StructType(
      Seq(
        StructField("Class", StringType, nullable = false),
        StructField("Precision", DoubleType, nullable = false),
        StructField("Recall", DoubleType, nullable = false),
        StructField("FPR", DoubleType, nullable = false),
        StructField("F1-score", DoubleType, nullable = false))
    )

    var metricsDF = spark.createDataFrame(spark.sparkContext.emptyRDD[Row], schema)

    labels.foreach { l =>
      var emptyMetricsDF = spark.createDataFrame(spark.sparkContext.emptyRDD[Row], schema)

      emptyMetricsDF = metricsDF.union(Seq((l, metrics.precision(l), metrics.recall(l), metrics.falsePositiveRate(l), metrics.fMeasure(l))).toDF())

      metricsDF = emptyMetricsDF
    }

    metricsDF.show()

    spark.stop()
    spark.close()
  }
}
