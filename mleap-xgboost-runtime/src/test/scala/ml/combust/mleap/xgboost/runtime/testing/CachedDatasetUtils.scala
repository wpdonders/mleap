package ml.combust.mleap.xgboost.runtime.testing

import ml.combust.mleap.core.types.{StructType, TensorType}
import ml.combust.mleap.core.util.VectorConverters
import ml.combust.mleap.runtime.frame.{ArrayRow, DefaultLeapFrame}
import ml.dmlc.xgboost4j.scala.DMatrix
import org.apache.spark.ml.linalg.SparseVector
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.mleap.TypeConverters

trait CachedDatasetUtils {

  private final val TrainDataFilePath = "datasources/agaricus.train"
  private final val TestDataFilePath = "datasources/agaricus.test"
  var mleapSchema: Option[StructType] = None

  lazy val denseDataset: DMatrix =
    new DMatrix(this.getClass.getClassLoader.getResource(TrainDataFilePath).getFile)

  lazy val leapFrameLibSVMtrain: DefaultLeapFrame = leapFrameFromLibSVMFile(TrainDataFilePath)
  lazy val leapFrameLibSVMtest: DefaultLeapFrame = leapFrameFromLibSVMFile(TestDataFilePath)
  lazy val numFeatures: Int =
    leapFrameLibSVMtrain.schema.getField("features").get.dataType.asInstanceOf[TensorType].dimensions.get.head

  private def leapFrameFromLibSVMFile(filePath: String): DefaultLeapFrame = {

    // Use Spark utils to load libsvm from disk
    val spark = SparkSession.builder()
      .master("local[2]")
      .appName("XGBoostRuntimeClassificationModelParitySpec")
      .getOrCreate()

    // This is the dataset used by dmls-XGBoost https://github.com/dmlc/xgboost/blob/master/demo/data/agaricus.txt.train
    val dataFrame = spark.read.format("libsvm")
      .load(this.getClass.getClassLoader.getResource(filePath).getFile)

    mleapSchema = Option(TypeConverters.sparkSchemaToMleapSchema(dataFrame))

    val mleapMatrix: Array[ArrayRow] = dataFrame.collect().map {
      r => ArrayRow(
        Seq(
          r.get(0),
          VectorConverters.sparkVectorToMleapTensor(r.get(1).asInstanceOf[SparseVector])
        ))
    }

    DefaultLeapFrame(mleapSchema.get, mleapMatrix)
  }

}
