package org.apache.spark.sql.internal

object SQLConf
{
  object get {
    val ansiEnabled = false
    val datetimeJava8ApiEnabled = true
    val legacyStatisticalAggregate = false
    val maxToStringFields = 10
    val maxPlanStringLength = 36*1025
    val tableRelationCacheSize = 1024
    val metadataCacheTTL = 1000
    val jsonFilterPushDown = true
    val charVarcharAsString = true
    val sessionLocalTimeZone = "America/New_York"
    val allowNegativeScaleOfDecimalEnabled = true
    val resolver = (a: String, b: String) => a.equalsIgnoreCase(b)
    val caseSensitiveAnalysis = false
    val decimalOperationsAllowPrecisionLoss = true
    val literalPickMinimumPrecision = true
    val integerGroupingIdEnabled = false
    val methodSplitThreshold = 50
    val codegenComments = false
    val codegenCacheMaxEntries = 30
    val loggingMaxLinesForCodegen = 20
    val legacySizeOfNull = false
    val setOpsPrecedenceEnforced = false
    val exponentLiteralAsDecimalEnabled = true
    val optimizerInSetSwitchThreshold = 10
    val supportQuotedRegexColumnName = false
    val escapedStringLiterals = true
  }
  object StoreAssignmentPolicy extends Enumeration {
    val ANSI, LEGACY, STRICT = Value
  }
}